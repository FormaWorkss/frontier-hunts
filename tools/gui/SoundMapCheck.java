import com.formaworks.frontierhunts.sound.FrontierSoundCategory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeSet;

/** [gui] Reads frontierhunts sound event names (one per line) and prints the Frontier sound group of each (tools/gui/run.sh). */
public final class SoundMapCheck {
   public static void main(String[] args) throws Exception {
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      Map<FrontierSoundCategory, TreeSet<String>> groups = new EnumMap<>(FrontierSoundCategory.class);
      TreeSet<String> unmapped = new TreeSet<>();
      String line;
      int n = 0;
      while ((line = in.readLine()) != null) {
         line = line.trim();
         if (line.isEmpty()) continue;
         n++;
         FrontierSoundCategory c = FrontierSoundCategory.classify(line);
         if (c == null || c == FrontierSoundCategory.MASTER) unmapped.add(line);
         else groups.computeIfAbsent(c, k -> new TreeSet<>()).add(line);
      }
      for (Map.Entry<FrontierSoundCategory, TreeSet<String>> e : groups.entrySet()) {
         System.out.println(e.getKey().label + " (" + e.getValue().size() + "): " + String.join(" ", e.getValue()));
      }
      // name rules for sounds other features add later
      String[] future = {"hound_bay", "bear_growl", "turkey_gobble", "weather_thunder_far", "atv_horn", "ui.journal_open",
         "crossbow_shot", "amb_cicada", "snowmobile_engine", "elk_call", "duck_call", "trailcam_click"};
      StringBuilder f = new StringBuilder("future-name rules:");
      for (String s : future) {
         FrontierSoundCategory c = FrontierSoundCategory.classify(s);
         f.append(' ').append(s).append('=').append(c == null ? "master-only" : c.name());
      }
      System.out.println(f);
      System.out.println(n + " events, " + unmapped.size() + " without a group" + (unmapped.isEmpty() ? "" : ": " + unmapped));
      if (!unmapped.isEmpty()) System.exit(1);
   }
}
