/*
 * [economy] Offline check of economy.ShopPrices.apply: every store entry gets a positive price, keys and order are kept,
 * entries the store does not sell are ignored.
 *   tools/compile.sh <repo> /tmp/claude-0/cc-economy
 *   javac -proc:none -cp /tmp/claude-0/cc-economy:$(cat /home/claude/fh/cp62.txt) -d /tmp/sh tools/economy/ShopHarness.java
 *   java -cp /tmp/sh:/tmp/claude-0/cc-economy:$(cat /home/claude/fh/cp62.txt) ShopHarness     -> PASS
 */
import java.util.*;
public class ShopHarness {
  public static void main(String[] a) {
    Map<String,Integer> shop = new LinkedHashMap<>();
    String[] ids={"rifle_round","shotgun_shell","pistol_round","tranquilizer_dart","bowfishing_arrow","medkit","bait","scent_cover","suppressor","extended_magazine","steady_stock","bipod","six_power_scope","eight_power_scope","twelve_power_scope","thermal_scope","fishing_drag_kit"};
    for (String s: ids) shop.put(s, 999);
    List<String> before = new ArrayList<>(shop.keySet());
    com.formaworks.frontierhunts.economy.ShopPrices.apply(shop);
    if (!before.equals(new ArrayList<>(shop.keySet()))) throw new AssertionError("order/keys changed");
    for (var e: shop.entrySet()) { if (e.getValue()==999 || e.getValue()<=0) throw new AssertionError("unpriced "+e.getKey()); System.out.println(e.getKey()+" = "+e.getValue()); }
    Map<String,Integer> partial = new LinkedHashMap<>(); partial.put("bait", 4);
    com.formaworks.frontierhunts.economy.ShopPrices.apply(partial);
    if (partial.size()!=1 || partial.get("bait")!=1) throw new AssertionError("partial");
    System.out.println("PASS");
  }
}
