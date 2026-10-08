import com.formaworks.frontierhunts.client.rack.RackGen;
import java.io.*;
import java.nio.*;

/** [1.1.8] writes racks for preview: RackDump <outdir> <family> <pointsL> <pointsR> <abnL> <abnR> <size> <variant> <seed> <name> */
public class RackDump {
   public static void main(String[] a) throws Exception {
      RackGen.Params p = new RackGen.Params();
      p.family = RackGen.Family.valueOf(a[1]);
      p.pointsL = Integer.parseInt(a[2]); p.pointsR = Integer.parseInt(a[3]);
      p.abnL = Integer.parseInt(a[4]); p.abnR = Integer.parseInt(a[5]);
      p.size = Float.parseFloat(a[6]); p.variant = Integer.parseInt(a[7]); p.seed = Long.parseLong(a[8]);
      p.pedicle = p.family == RackGen.Family.ELK ? 0.0766F : p.family == RackGen.Family.MOOSE ? 0.0832F : 0.072F;
      p.mass = 1.0F + Math.max(0, p.size - 1.0F) * 0.6F;
      RackGen.Mesh m = RackGen.build(p, 8);
      try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(a[0] + "/" + a[9] + ".rack")))) {
         o.writeInt(m.vertices); o.writeInt(m.triangles);
         for (int i = 0; i < m.vertices * 3; i++) o.writeFloat(m.pos[i]);
         for (int i = 0; i < m.vertices * 3; i++) o.writeFloat(m.nrm[i]);
         for (int i = 0; i < m.vertices * 2; i++) o.writeFloat(m.uv[i]);
         for (int i = 0; i < m.vertices; i++) o.writeFloat(m.shade[i]);
         for (int i = 0; i < m.triangles * 3; i++) o.writeInt(m.tri[i]);
      }
      System.out.println(a[9] + " v=" + m.vertices + " t=" + m.triangles);
   }
}
