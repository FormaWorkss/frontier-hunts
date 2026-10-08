package com.formaworks.frontierhunts.phone.client.ui.apps;

import com.formaworks.frontierhunts.phone.client.ui.App;
import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.PhoneUi;
import java.util.List;

/** [phone] The Field Phone's apps, in home-screen order, and the four in the dock. */
public final class Apps {
   /** [1.4.0] the Camera leads the dock (it replaced the Field Camera); Messages joined it for the photos and emoji */
   public static final List<String> DOCK = List.of("camera", "cams", "maps", "messages");

   private Apps() {
   }

   public static PhoneUi create(PhoneModel model, PhoneActions actions) {
      // [1.4.0] the Torch is the quick toggle on the lock screen and in the shade (its app made room for the Camera)
      List<App> apps = List.of(new ClockApp(), new CompassApp(), new WalletApp(), new TrophiesApp(), new JournalApp(), new CallsApp(), new ContractsApp(),
         new WeatherApp(), new ChessApp(), new DiceApp(), new FlushApp(), new SettingsApp(), new CameraApp(), new CamsApp(), new MapsApp(), new MessagesApp());
      return new PhoneUi(model, actions, apps, DOCK);
   }
}
