import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * FrontierHunts release builder. One file, plain JDK 21, no Gradle, no Python - so the same release can be rebuilt on
 * any machine that has Java 21 (Windows or Linux) and checked byte for byte against the published SHA-256.
 *
 * <pre>
 * java tools/release/BuildRelease.java --repo &lt;source folder&gt; --base &lt;base jar or its .part1&gt;
 *      --libs &lt;folder;folder...&gt; [--neoforge &lt;neoforge-21.1.248.jar&gt;] [--version 1.1.5] [--out &lt;folder&gt;]
 * java tools/release/BuildRelease.java --compare &lt;a.jar&gt; &lt;b.jar&gt;
 * </pre>
 *
 * What it does (the same steps the earlier tools/build.py did):
 * <ol>
 * <li>compiles {@code src/} (FrontierHunts) and {@code fs/java/} (the bundled Frontier Structures) against the base
 *     jar, NeoForge 21.1.248 and the libraries listed in {@code tools/release/classpath.txt};</li>
 * <li>starts from the base jar (FrontierHunts 0.1.0-dev.62-gear.3, PNGs already losslessly optimized), drops every
 *     class family that {@code src} recompiles and the paths listed in {@code patch/_remove/*.txt};</li>
 * <li>adds {@code patch/**} and {@code fs/resources/**}, deep-merges the JSON fragments in {@code patch/_merge/};</li>
 * <li>stamps the version from {@code VERSION} into the mod metadata, writes every JSON compactly, every entry at a fixed
 *     date, deflated at level 9 (sounds stored), in a fixed order - so the output depends only on the inputs.</li>
 * </ol>
 * Libraries are looked up by Maven path (group/artifact/version/file) under each {@code --libs} folder, so a Gradle
 * cache ({@code .gradle/caches}) works as is.
 */
public final class BuildRelease {
   static final String VER_OLD = "0.1.0-dev.62-landscape.62-gear.3";
   static final String FS_VERSION = "1.2.0";
   static final LocalDateTime FIXED = LocalDateTime.of(1980, 2, 1, 0, 0);

   public static void main(String[] args) throws Exception {
      Map<String, String> a = new HashMap<>();
      for (int i = 0; i < args.length; i++) {
         if (args[i].equals("--compare")) {
            System.exit(compare(Path.of(args[i + 1]), Path.of(args[i + 2])) ? 0 : 1);
         }
         if (args[i].startsWith("--") && i + 1 < args.length) {
            a.put(args[i].substring(2), args[++i]);
         }
      }
      Path repo = Path.of(a.getOrDefault("repo", ".")).toAbsolutePath().normalize();
      String version = a.containsKey("version") ? a.get("version") : Files.readString(repo.resolve("VERSION")).trim();
      Path out = Path.of(a.getOrDefault("out", repo.resolve(".build").toString())).toAbsolutePath();
      Path work = out.resolve("work-" + version);
      Files.createDirectories(out);
      if (!a.containsKey("base")) {
         die("--base <base jar or .part1> is required");
      }
      Path base = joinParts(Path.of(a.get("base")), work);
      List<Path> libs = resolveLibs(repo, a.getOrDefault("libs", ""), a.get("neoforge"));
      libs.add(0, base);
      String cp = String.join(File.pathSeparator, libs.stream().map(Path::toString).toList());

      Path cls = work.resolve("classes"), fscls = work.resolve("fsclasses");
      javac(repo.resolve("src"), cls, cp);
      javac(repo.resolve(Path.of("fs", "java")), fscls, cls + File.pathSeparator + cp);

      Map<String, byte[]> entries = new TreeMap<>();
      Set<String> families = new HashSet<>();
      addTree(cls, entries, families);
      addTree(fscls, entries, null);
      addTree(repo.resolve("patch"), entries, null);
      addTree(repo.resolve(Path.of("fs", "resources")), entries, null);

      Map<String, List<Object>> fragments = new TreeMap<>();
      Path mroot = repo.resolve(Path.of("patch", "_merge"));
      if (Files.isDirectory(mroot)) {
         List<Path> files;
         try (Stream<Path> s = Files.walk(mroot)) {
            files = s.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json")).sorted(Comparator.comparing(p -> rel(mroot, p))).toList();
         }
         for (Path f : files) {
            String target = rel(mroot, f.getParent());
            fragments.computeIfAbsent(target, k -> new ArrayList<>()).add(Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
         }
      }
      Set<String> remove = new HashSet<>();
      Path rroot = repo.resolve(Path.of("patch", "_remove"));
      if (Files.isDirectory(rroot)) {
         try (Stream<Path> s = Files.list(rroot)) {
            for (Path f : s.filter(p -> p.toString().endsWith(".txt")).sorted().toList()) {
               for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                  line = line.split("#", 2)[0].trim();
                  if (!line.isEmpty()) {
                     remove.add(line);
                  }
               }
            }
         }
      }

      Path jar = out.resolve("FrontierHunts-" + version + ".jar");
      int removed = 0;
      try (ZipFile zi = new ZipFile(base.toFile()); ZipOutputStream zo = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(jar)))) {
         zo.setLevel(9);
         Set<String> written = new HashSet<>();
         for (Enumeration<? extends ZipEntry> en = zi.entries(); en.hasMoreElements(); ) {
            ZipEntry e = en.nextElement();
            String n = e.getName();
            if (n.endsWith(".class") && families.contains(family(n))) {
               removed++;
               continue;
            }
            if (entries.containsKey(n) || fragments.containsKey(n)) {
               continue;
            }
            if (remove.contains(n)) {
               removed++;
               continue;
            }
            byte[] data = zi.getInputStream(e).readAllBytes();
            if (n.equals("META-INF/neoforge.mods.toml")) {
               data = modsToml(new String(data, StandardCharsets.UTF_8), version).getBytes(StandardCharsets.UTF_8);
            } else if (n.equals("META-INF/MANIFEST.MF")) {
               data = new String(data, StandardCharsets.UTF_8).replace("Implementation-Version: " + VER_OLD, "Implementation-Version: " + version)
                  .getBytes(StandardCharsets.UTF_8);
            }
            put(zo, written, n, data);
         }
         for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!fragments.containsKey(e.getKey())) {
               put(zo, written, e.getKey(), e.getValue());
            }
         }
         for (Map.Entry<String, List<Object>> e : fragments.entrySet()) {
            String n = e.getKey();
            Object merged;
            if (entries.containsKey(n)) {
               merged = Json.parse(new String(entries.get(n), StandardCharsets.UTF_8));
            } else if (zi.getEntry(n) != null) {
               merged = Json.parse(new String(zi.getInputStream(zi.getEntry(n)).readAllBytes(), StandardCharsets.UTF_8));
            } else {
               merged = new LinkedHashMap<String, Object>();
            }
            for (Object f : e.getValue()) {
               merged = Json.merge(merged, f);
            }
            put(zo, written, n, Json.write(merged).getBytes(StandardCharsets.UTF_8));
         }
      }
      System.out.println("removed " + removed + ", overrides " + entries.size() + ", merged " + fragments.size());
      System.out.println(jar);
      System.out.println("SHA-256 " + sha256(Files.readAllBytes(jar)));
      System.out.println("content " + contentDigest(jar));
   }

   // ------------------------------------------------------------------------------------------------ steps

   /** [1.4.0] launch: what the mod is today (the old description still sold the long-retired base station) */
   static final String DESCRIPTION = "Frontier Hunts by FormaWorks: realistic hunting for Minecraft. Whitetail, elk, moose and fourteen more species "
      + "with real anatomy, shot placement and blood trails, living in herds that move like the real thing. Wind, thermals and scent "
      + "(a carbon base layer hides yours), the rut with rubs, scrapes and calling. Bows, rifles and attachments from the Gunsmith's Bench, "
      + "ammo and arrows from the Reloading Bench, everything else from the Frontier Workbench. Shooting sticks, tree stands, blinds and "
      + "tents; trail cameras; the Field Phone with its camera, selfies, texts with photos and emoji, maps, weather, contracts and games "
      + "with friends. A guided first hunt, the Ranger Academy, licences and tags, a hunting-season campaign, skills and co-op progression; "
      + "fishing, butchering and taxidermy; snowmobiles and ATVs; Vanilla and Ultra graphics presets that work with any shader pack.";

   static String modsToml(String t, String version) {
      t = t.replace("version=\"" + VER_OLD + "\"", "version=\"" + version + "\"");
      t = t.replace("displayName=\"Frontier Hunts\"", "displayName=\"FrontierHunts\"");
      String open = "description=" + "'".repeat(3);
      int d0 = t.indexOf(open), d1 = d0 < 0 ? -1 : t.indexOf("'".repeat(3), d0 + open.length());
      if (d0 >= 0 && d1 > d0 && t.indexOf("modId=\"frontierhunts\"") < d0) {
         t = t.substring(0, d0) + open + DESCRIPTION + t.substring(d1);
      }
      t = t.replaceFirst("authors=\"FormaWorks\"", "authors=\"FormaWorks\"");
      t = t.replace("audio: asset-specific licenses in FRONTIER_AUDIO_CREDITS.txt and WILDLIFE_AUDIO_LICENSES.md", "audio: asset-specific licences in AUDIO_CREDITS.txt");
      // [1.1.5] any NeoForge 21.1 build from 248 on, not exactly 248 (players on a newer 21.1 NeoForge could not load it)
      t = t.replace("versionRange=\"[21.1.248]\"", "versionRange=\"[21.1.248,)\"");
      if (!t.contains("modId=\"frontierstructures\"")) {
         int i = t.indexOf("\n[[mixins]]");
         if (i < 0) {
            die("mods.toml: no [[mixins]] section to insert Frontier Structures before");
         }
         t = t.substring(0, i) + "\n[[mods]]\nmodId=\"frontierstructures\"\nversion=\"" + FS_VERSION + "\"\ndisplayName=\"Frontier Structures\"\n"
            + "authors=\"FormaWorks\"\ndescription='''Furnished frontier settlements, lookout towers and a persistent structure editing workshop, bundled with Frontier Hunts.'''\n\n"
            + "[[dependencies.frontierstructures]]\nmodId=\"neoforge\"\ntype=\"required\"\nversionRange=\"[21.1.248,)\"\nordering=\"NONE\"\nside=\"BOTH\"\n\n"
            + "[[dependencies.frontierstructures]]\nmodId=\"frontierhunts\"\ntype=\"required\"\nversionRange=\"[0,)\"\nordering=\"AFTER\"\nside=\"BOTH\"\n"
            + t.substring(i);
      }
      return t;
   }

   static void put(ZipOutputStream zo, Set<String> written, String n, byte[] data) throws IOException {
      if (!written.add(n)) {
         return;
      }
      if (n.endsWith(".json") && !n.startsWith("META-INF/")) {
         try {
            data = Json.write(Json.parse(new String(data, StandardCharsets.UTF_8))).getBytes(StandardCharsets.UTF_8);
         } catch (RuntimeException e) {
            // not valid JSON: shipped as is
         }
      }
      ZipEntry e = new ZipEntry(n);
      e.setTimeLocal(FIXED);
      if (n.endsWith(".ogg")) {
         CRC32 crc = new CRC32();
         crc.update(data);
         e.setMethod(ZipEntry.STORED);
         e.setSize(data.length);
         e.setCompressedSize(data.length);
         e.setCrc(crc.getValue());
      } else {
         e.setMethod(ZipEntry.DEFLATED);
      }
      zo.putNextEntry(e);
      zo.write(data);
      zo.closeEntry();
   }

   static String family(String n) {
      return n.split("\\$")[0].replace(".class", "");
   }

   static void addTree(Path root, Map<String, byte[]> entries, Set<String> families) throws IOException {
      if (!Files.isDirectory(root)) {
         return;
      }
      List<Path> files;
      try (Stream<Path> s = Files.walk(root)) {
         files = s.filter(Files::isRegularFile).toList();
      }
      for (Path p : files) {
         String rel = rel(root, p);
         if (rel.startsWith("_merge/") || rel.startsWith("_remove/") || rel.startsWith("META-INF/neoforge.mods.toml")) {
            continue;
         }
         entries.put(rel, Files.readAllBytes(p));
         if (families != null) {
            families.add(family(rel));
         }
      }
   }

   static String rel(Path root, Path p) {
      return root.relativize(p).toString().replace('\\', '/');
   }

   static void javac(Path srcdir, Path outdir, String cp) throws IOException {
      deleteTree(outdir);
      Files.createDirectories(outdir);
      if (!Files.isDirectory(srcdir)) {
         return;
      }
      List<String> files;
      try (Stream<Path> s = Files.walk(srcdir)) {
         files = s.filter(p -> p.toString().endsWith(".java")).map(Path::toString).sorted().toList();
      }
      if (files.isEmpty()) {
         return;
      }
      JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
      if (jc == null) {
         die("no Java compiler: run this with a JDK 21, not a JRE");
      }
      Path argf = outdir.resolveSibling(outdir.getFileName() + ".args");
      Files.write(argf, files, StandardCharsets.UTF_8);
      ByteArrayOutputStream err = new ByteArrayOutputStream();
      int rc = jc.run(null, err, err, "-proc:none", "--release", "21", "-nowarn", "-encoding", "UTF-8", "-cp", cp, "-d", outdir.toString(), "@" + argf);
      if (rc != 0) {
         System.err.println(err.toString(StandardCharsets.UTF_8));
         die("javac failed for " + srcdir);
      }
      System.out.println("compiled " + files.size() + " sources in " + srcdir.getFileName());
   }

   /** a split base jar (name.jar.part1, .part2, ...) is joined into the work folder */
   static Path joinParts(Path p, Path work) throws IOException {
      String name = p.getFileName().toString();
      if (!name.matches(".*\\.part1$")) {
         return p;
      }
      String stem = name.substring(0, name.length() - ".part1".length());
      Path joined = work.resolve(stem);
      Files.createDirectories(work);
      try (OutputStream o = Files.newOutputStream(joined)) {
         for (int i = 1; ; i++) {
            Path part = p.resolveSibling(stem + ".part" + i);
            if (!Files.exists(part)) {
               break;
            }
            Files.copy(part, o);
         }
      }
      return joined;
   }

   static List<Path> resolveLibs(Path repo, String roots, String neoforge) throws IOException {
      List<Path> rootList = new ArrayList<>();
      for (String r : roots.split("[;" + File.pathSeparator + "]")) {
         if (!r.isBlank()) {
            Path rp = Path.of(r.trim());
            rootList.add(rp);
            rootList.add(rp.resolve("caches"));
         }
      }
      List<Path> out = new ArrayList<>();
      List<String> missing = new ArrayList<>();
      for (String line : Files.readAllLines(repo.resolve(Path.of("tools", "release", "classpath.txt")))) {
         line = line.split("#", 2)[0].trim();
         if (line.isEmpty()) {
            continue;
         }
         if (line.equals("neoforge")) {
            Path nf = neoforge != null ? Path.of(neoforge) : null;
            if (nf == null || !Files.isRegularFile(nf)) {
               missing.add("neoforge-21.1.248.jar (pass --neoforge, e.g. build/moddev/artifacts/neoforge-21.1.248.jar)");
            } else {
               out.add(nf);
            }
            continue;
         }
         // group/artifact/version/file.jar, under modules-2/files-2.1/group/artifact/version/<hash>/file.jar
         String[] q = line.split("/");
         Path found = null;
         for (Path r : rootList) {
            Path dir = r.resolve(Path.of("modules-2", "files-2.1", q[0], q[1], q[2]));
            if (Files.isDirectory(dir)) {
               try (Stream<Path> s = Files.list(dir)) {
                  for (Path h : s.sorted().toList()) {
                     if (Files.isRegularFile(h.resolve(q[3]))) {
                        found = h.resolve(q[3]);
                        break;
                     }
                  }
               }
            }
            if (found != null) {
               break;
            }
         }
         if (found == null) {
            missing.add(line);
         } else {
            out.add(found);
         }
      }
      if (!missing.isEmpty()) {
         die("libraries not found under --libs:\n  " + String.join("\n  ", missing));
      }
      return out;
   }

   // ------------------------------------------------------------------------------------------------ checks

   static String sha256(byte[] d) throws Exception {
      return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(d));
   }

   /** a digest of what is inside the jar (names and contents), independent of compression */
   static String contentDigest(Path jar) throws Exception {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      for (Map.Entry<String, String> e : contents(jar).entrySet()) {
         md.update((e.getKey() + "\0" + e.getValue() + "\n").getBytes(StandardCharsets.UTF_8));
      }
      return HexFormat.of().withUpperCase().formatHex(md.digest());
   }

   static Map<String, String> contents(Path jar) throws Exception {
      Map<String, String> m = new TreeMap<>();
      try (ZipFile z = new ZipFile(jar.toFile())) {
         for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
            ZipEntry e = en.nextElement();
            if (!e.isDirectory()) {
               m.put(e.getName(), sha256(z.getInputStream(e).readAllBytes()));
            }
         }
      }
      return m;
   }

   static boolean compare(Path a, Path b) throws Exception {
      Map<String, String> x = contents(a), y = contents(b);
      int diff = 0;
      for (String n : new TreeSet<>(union(x.keySet(), y.keySet()))) {
         String p = x.get(n), q = y.get(n);
         if (!Objects.equals(p, q)) {
            if (diff++ < 40) {
               System.out.println((p == null ? "only in " + b.getFileName() : q == null ? "only in " + a.getFileName() : "differs") + ": " + n);
            }
         }
      }
      System.out.println(diff == 0 ? "SAME CONTENT (" + x.size() + " files)" : diff + " file(s) differ");
      return diff == 0;
   }

   static Set<String> union(Set<String> a, Set<String> b) {
      Set<String> s = new HashSet<>(a);
      s.addAll(b);
      return s;
   }

   static void deleteTree(Path p) throws IOException {
      if (!Files.exists(p)) {
         return;
      }
      try (Stream<Path> s = Files.walk(p)) {
         for (Path q : s.sorted(Comparator.reverseOrder()).toList()) {
            Files.delete(q);
         }
      }
   }

   static void die(String msg) {
      System.err.println("BUILD FAILED: " + msg);
      System.exit(2);
   }

   // ------------------------------------------------------------------------------------------------ JSON

   /** Minimal JSON: objects keep key order, numbers keep their exact text, null is {@link #NULL}. */
   static final class Json {
      static final Object NULL = new Object() {
         @Override
         public String toString() {
            return "null";
         }
      };

      record Num(String text) {
      }

      private final String s;
      private int i;

      private Json(String s) {
         this.s = s;
      }

      static Object parse(String text) {
         Json j = new Json(text.startsWith("﻿") ? text.substring(1) : text);
         Object v = j.value();
         j.ws();
         if (j.i != j.s.length()) {
            throw new IllegalArgumentException("trailing data at " + j.i);
         }
         return v;
      }

      private void ws() {
         while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) {
            i++;
         }
      }

      private Object value() {
         ws();
         if (i >= s.length()) {
            throw new IllegalArgumentException("unexpected end");
         }
         char c = s.charAt(i);
         switch (c) {
            case '{': {
               i++;
               Map<String, Object> m = new LinkedHashMap<>();
               ws();
               if (s.charAt(i) == '}') {
                  i++;
                  return m;
               }
               while (true) {
                  ws();
                  String k = str();
                  ws();
                  expect(':');
                  m.put(k, value());
                  ws();
                  if (s.charAt(i) == ',') {
                     i++;
                  } else {
                     expect('}');
                     return m;
                  }
               }
            }
            case '[': {
               i++;
               List<Object> l = new ArrayList<>();
               ws();
               if (s.charAt(i) == ']') {
                  i++;
                  return l;
               }
               while (true) {
                  l.add(value());
                  ws();
                  if (s.charAt(i) == ',') {
                     i++;
                  } else {
                     expect(']');
                     return l;
                  }
               }
            }
            case '"':
               return str();
            default:
               if (s.startsWith("true", i)) {
                  i += 4;
                  return Boolean.TRUE;
               }
               if (s.startsWith("false", i)) {
                  i += 5;
                  return Boolean.FALSE;
               }
               if (s.startsWith("null", i)) {
                  i += 4;
                  return NULL;
               }
               int st = i;
               while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                  i++;
               }
               if (st == i) {
                  throw new IllegalArgumentException("bad value at " + i);
               }
               return new Num(s.substring(st, i));
         }
      }

      private void expect(char c) {
         if (i >= s.length() || s.charAt(i) != c) {
            throw new IllegalArgumentException("expected " + c + " at " + i);
         }
         i++;
      }

      private String str() {
         expect('"');
         StringBuilder b = new StringBuilder();
         while (true) {
            char c = s.charAt(i++);
            if (c == '"') {
               return b.toString();
            }
            if (c == '\\') {
               char e = s.charAt(i++);
               switch (e) {
                  case 'n' -> b.append('\n');
                  case 't' -> b.append('\t');
                  case 'r' -> b.append('\r');
                  case 'b' -> b.append('\b');
                  case 'f' -> b.append('\f');
                  case 'u' -> {
                     b.append((char)Integer.parseInt(s.substring(i, i + 4), 16));
                     i += 4;
                  }
                  default -> b.append(e);
               }
            } else {
               b.append(c);
            }
         }
      }

      static String write(Object v) {
         StringBuilder b = new StringBuilder();
         write(v, b);
         return b.toString();
      }

      @SuppressWarnings("unchecked")
      private static void write(Object v, StringBuilder b) {
         if (v instanceof Map<?, ?> m) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>)m).entrySet()) {
               if (!first) {
                  b.append(',');
               }
               first = false;
               quote(e.getKey(), b);
               b.append(':');
               write(e.getValue(), b);
            }
            b.append('}');
         } else if (v instanceof List<?> l) {
            b.append('[');
            for (int k = 0; k < l.size(); k++) {
               if (k > 0) {
                  b.append(',');
               }
               write(l.get(k), b);
            }
            b.append(']');
         } else if (v instanceof String str) {
            quote(str, b);
         } else if (v instanceof Num n) {
            b.append(n.text());
         } else {
            b.append(v == NULL ? "null" : v.toString());
         }
      }

      private static void quote(String str, StringBuilder b) {
         b.append('"');
         for (int k = 0; k < str.length(); k++) {
            char c = str.charAt(k);
            switch (c) {
               case '"' -> b.append("\\\"");
               case '\\' -> b.append("\\\\");
               case '\n' -> b.append("\\n");
               case '\r' -> b.append("\\r");
               case '\t' -> b.append("\\t");
               case '\b' -> b.append("\\b");
               case '\f' -> b.append("\\f");
               default -> {
                  if (c < 0x20) {
                     b.append(String.format("\\u%04x", (int)c));
                  } else {
                     b.append(c);
                  }
               }
            }
         }
         b.append('"');
      }

      /** fragments: objects merge key by key (a null value deletes the key), lists are unioned in order, the rest replaces */
      @SuppressWarnings("unchecked")
      static Object merge(Object a, Object b) {
         if (a instanceof Map<?, ?> ma && b instanceof Map<?, ?> mb) {
            Map<String, Object> o = new LinkedHashMap<>((Map<String, Object>)ma);
            for (Map.Entry<String, Object> e : ((Map<String, Object>)mb).entrySet()) {
               if (e.getValue() == NULL) {
                  o.remove(e.getKey());
               } else {
                  o.put(e.getKey(), o.containsKey(e.getKey()) ? merge(o.get(e.getKey()), e.getValue()) : e.getValue());
               }
            }
            return o;
         }
         if (a instanceof List<?> la && b instanceof List<?> lb) {
            List<Object> o = new ArrayList<>(la);
            for (Object v : lb) {
               if (!o.contains(v)) {
                  o.add(v);
               }
            }
            return o;
         }
         return b;
      }
   }
}
