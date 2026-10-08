import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.PhoneUi;
import java.util.function.IntConsumer;

/** [phone] Mock scenes: which app, which page, what state. */
final class Scenes {
   static final String[] ALL = {"lock", "home", "journal", "messages", "messages_thread", "weather", "weather_low", "clock", "compass", "cams", "cams_roll", "cams_photo", "maps", "maps_places", "contracts", "contracts_board", "wallet", "trophies", "calls", "torch", "settings", "night", "chess_lobby", "chess", "chess_puzzle", "dice", "dice_lobby", "flush", "flush_lobby", "camera", "camera_view", "camera_send", "messages_emoji"};

   static final class Setup {
      int frames = 70;
      float mouseX = -1, mouseY = -1;
      IntConsumer perFrame;
   }

   private Scenes() {
   }

   static void sleep(int ms) {
      try {
         Thread.sleep(ms);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   static Setup setup(String scene, PhoneUi ui, PhoneModel m, MockMain.Stub a) {
      Setup s = new Setup();
      switch (scene) {
         case "lock" -> ui.open(null);
         case "camera" -> ui.open("camera");
         case "camera_view" -> {
            ui.open("camera");
            s.frames = 90;
            s.perFrame = i -> {
               if (i == 2) {
                  ui.current().tap(102, 1L);
               }
            };
         }
         case "camera_send" -> {
            ui.open("camera");
            s.frames = 90;
            s.perFrame = i -> {
               if (i == 2) {
                  ui.current().tap(102, 0L);
               }
               if (i == 40) {
                  ui.current().tap(105, 0L);
               }
            };
         }
         case "messages_emoji" -> {
            ui.open("messages");
            s.perFrame = i -> {
               if (i == 2) {
                  ui.current().tap(100, 0L);
               }
               if (i == 20) {
                  ui.current().tap(108, 0L);
               }
            };
         }
         case "messages_thread" -> {
            ui.open("messages");
            s.perFrame = i -> {
               if (i == 2) {
                  ui.current().tap(100, 0L);
               }
            };
         }
         case "home" -> {
            ui.open(null);
            s.perFrame = i -> {
               if (i == 1) {
                  ui.unlock();
               }
            };
         }
         case "weather_low" -> {
            ui.open("weather");
            s.perFrame = i -> {
               if (i == 3) {
                  ui.current().scroller().to(330);
               }
            };
         }
         case "cams_roll" -> {
            ui.open("cams");
            s.perFrame = i -> {
               if (i == 2) {
                  ui.current().tap(100, 0L);
               }
            };
         }
         case "cams_photo" -> {
            ui.open("cams");
            s.frames = 110;
            s.perFrame = i -> {
               if (i == 2) {
                  ui.current().tap(100, 0L);
               }
               if (i == 30) {
                  ui.current().tap(101, 0L);
               }
            };
         }
         case "maps_places" -> {
            ui.open("maps");
            s.perFrame = i -> {
               if (i == 2) {
                  ui.current().tap(100, 1L);
               }
            };
         }
         case "contracts_board" -> {
            ui.open("contracts");
            s.perFrame = i -> {
               if (i == 2) {
                  ui.current().tap(100, 1L);
               }
            };
         }
         case "night" -> {
            m.settings.night = true;
            m.dayTime = Sample.at(21, 30);
            ui.open("compass");
         }
         case "chess" -> {
            ui.open("chess");
            s.frames = 260;
            int[][] mine = {{12, 28}, {6, 21}, {5, 26}, {1, 18}};
            s.perFrame = i -> {
               if (i == 2) {
                  ui.current().tap(101, 0L);
               }
               for (int k = 0; k < mine.length; k++) {
                  if (i == 10 + k * 50) {
                     ui.current().tap(102, mine[k][0]);
                  }
                  if (i == 11 + k * 50) {
                     ui.current().tap(102, mine[k][1]);
                  }
                  if (i > 11 + k * 50 && i < 40 + k * 50) {
                     sleep(4);
                  }
               }
               if (i == 250) {
                  ui.current().tap(102, 3);
               }
            };
         }
         case "chess_puzzle" -> {
            ui.open("chess");
            s.frames = 60;
            s.perFrame = i -> {
               if (i == 2) {
                  ui.current().tap(100, 0L);
               }
            };
         }
         case "dice" -> {
            ui.open("dice");
            s.frames = 900;
            s.perFrame = i -> {
               if (i == 2) {
                  ui.current().tap(101, 0L);
               }
               // our turns: roll, hold the first two dice, roll, score the best open box
               if (i % 150 == 10) {
                  ui.current().tap(103, 0L);
               }
               if (i % 150 == 60) {
                  ui.current().tap(102, 0L);
                  ui.current().tap(102, 1L);
               }
               if (i % 150 == 70 && i < 800) {
                  ui.current().tap(103, 0L);
               }
               if (i % 150 == 120 && i < 800) {
                  int[] order = {12, 6, 5, 4, 3, 7};
                  ui.current().tap(104, order[(i / 150) % order.length]);
               }
               if (i > 120) {
                  sleep(2);
               }
            };
         }
         case "flush" -> {
            ui.open("flush");
            s.frames = Integer.getInteger("flushFrames", 330);
            s.perFrame = i -> {
               if (i == 2) {
                  ui.current().tap(101, 0L);
               }
               if (i > 120 && i % 9 == 0) {
                  try {
                     java.lang.reflect.Field fg = ui.current().getClass().getDeclaredField("game");
                     fg.setAccessible(true);
                     com.formaworks.frontierhunts.phone.games.Flush g = (com.formaworks.frontierhunts.phone.games.Flush)fg.get(ui.current());
                     for (com.formaworks.frontierhunts.phone.games.Flush.Bird b : g.birds) {
                        if (b.flying(g.tick) && b.y(g.tick) < 30 && !b.kind.protectedBird) {
                           float k = 342.0F / 100.0F;
                           float oy = (162.0F - 48.0F * k) / 2.0F;
                           float x = b.x(g.tick + 2) * k, y = b.y(g.tick + 2) * k + oy;
                           ui.current().mouse(x, y);
                           if (i < 320) {
                              ui.current().press(x, y, 0);
                           }
                           break;
                        }
                     }
                  } catch (Exception e) {
                     throw new RuntimeException(e);
                  }
               }
            };
         }
         case "chess_lobby" -> ui.open("chess");
         case "dice_lobby" -> ui.open("dice");
         case "flush_lobby" -> ui.open("flush");
         default -> ui.open(scene);
      }
      return s;
   }
}
