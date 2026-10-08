package com.formaworks.frontierhunts.livingworld.plan;

import java.util.LinkedHashMap;
import java.util.Map;

/** [livingworld] Block state specs as strings ("ns:id[k=v,...]") and their rotation by quarter turns. */
public final class Spec {
   private Spec() {
   }

   public static String id(String spec) {
      int i = spec.indexOf('[');
      return i < 0 ? spec : spec.substring(0, i);
   }

   public static Map<String, String> props(String spec) {
      Map<String, String> out = new LinkedHashMap<>();
      int i = spec.indexOf('[');
      if (i < 0) {
         return out;
      }
      String body = spec.substring(i + 1, spec.length() - 1);
      if (body.isEmpty()) {
         return out;
      }
      for (String kv : body.split(",")) {
         int e = kv.indexOf('=');
         if (e > 0) {
            out.put(kv.substring(0, e).trim(), kv.substring(e + 1).trim());
         }
      }
      return out;
   }

   public static String of(String id, Map<String, String> props) {
      if (props.isEmpty()) {
         return id;
      }
      StringBuilder b = new StringBuilder(id).append('[');
      boolean first = true;
      for (Map.Entry<String, String> e : props.entrySet()) {
         if (!first) {
            b.append(',');
         }
         first = false;
         b.append(e.getKey()).append('=').append(e.getValue());
      }
      return b.append(']').toString();
   }

   public static String with(String spec, String key, String value) {
      Map<String, String> p = props(spec);
      p.put(key, value);
      return of(id(spec), p);
   }

   public static String get(String spec, String key) {
      return props(spec).get(key);
   }

   /** rotates every direction-dependent property clockwise by quarter turns */
   public static String rotate(String spec, int quarters) {
      quarters &= 3;
      if (quarters == 0 || spec.indexOf('[') < 0) {
         return spec;
      }
      Map<String, String> in = props(spec);
      Map<String, String> out = new LinkedHashMap<>();
      for (Map.Entry<String, String> e : in.entrySet()) {
         String k = e.getKey();
         String v = e.getValue();
         Dir asKey = Dir.of(k);
         if (asKey != null) {
            out.put(asKey.rot(quarters).id(), v);
            continue;
         }
         switch (k) {
            case "facing", "horizontal_facing" -> {
               Dir d = Dir.of(v);
               out.put(k, d == null ? v : d.rot(quarters).id());
            }
            case "axis" -> out.put(k, (quarters & 1) == 1 ? ("x".equals(v) ? "z" : "z".equals(v) ? "x" : v) : v);
            case "rotation" -> {
               try {
                  out.put(k, Integer.toString(Math.floorMod(Integer.parseInt(v) + 4 * quarters, 16)));
               } catch (NumberFormatException ex) {
                  out.put(k, v);
               }
            }
            default -> out.put(k, v);
         }
      }
      return of(id(spec), out);
   }
}
