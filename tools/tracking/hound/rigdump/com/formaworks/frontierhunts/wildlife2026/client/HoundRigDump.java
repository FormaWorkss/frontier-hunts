package com.formaworks.frontierhunts.wildlife2026.client;

import com.formaworks.frontierhunts.tracking.client.HoundRig;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Locale;

/**
 * [hound3] Offline driver for the in-game rig: runs the real HoundRig.pose on the real .fhsk for poses read from
 * stdin ("name key=value ..." per line, keys = HoundRig.Input fields) and prints the 3x4 bone matrices, so the
 * python previews (tools/tracking/hound/rigview.py) show exactly what the game draws.
 */
public final class HoundRigDump {
   public static void main(String[] a) throws Exception {
      SkinnedMesh m;
      try (FileInputStream in = new FileInputStream(a[0])) {
         m = SkinnedMesh.parse(in);
      }
      BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
      String line;
      float[] out = new float[m.names.length * 12];
      while ((line = r.readLine()) != null) {
         line = line.trim();
         if (line.isEmpty()) {
            continue;
         }
         String[] t = line.split("\\s+");
         HoundRig.Input in = new HoundRig.Input();
         for (int i = 1; i < t.length; i++) {
            String[] kv = t[i].split("=");
            if (kv[0].equals("water")) {
               in.water = Boolean.parseBoolean(kv[1]);
               continue;
            }
            HoundRig.Input.class.getField(kv[0]).setFloat(in, Float.parseFloat(kv[1]));
         }
         HoundRig.pose(m, in, out);
         StringBuilder sb = new StringBuilder(t[0]);
         for (float f : out) {
            sb.append(' ').append(String.format(Locale.ROOT, "%.6f", f));
         }
         System.out.println(sb);
      }
   }
}
