package com.formaworks.frontierstructures;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class WorkshopData extends SavedData {
    public int floorCursor,placed;
    public boolean ready;
    public int reviewFloorCursor,reviewPlaced;
    public boolean reviewReady;
    public int latestFloorCursor,latestPlaced;public boolean latestReady;
    public int areaCursor(int a){return a==2?latestFloorCursor:a==1?reviewFloorCursor:floorCursor;}
    public int areaPlaced(int a){return a==2?latestPlaced:a==1?reviewPlaced:placed;}
    public boolean areaReady(int a){return a==2?latestReady:a==1?reviewReady:ready;}
    public void setAreaCursor(int a,int n){if(a==2)latestFloorCursor=n;else if(a==1)reviewFloorCursor=n;else floorCursor=n;}
    public void setAreaPlaced(int a,int n){if(a==2)latestPlaced=n;else if(a==1)reviewPlaced=n;else placed=n;}
    public void setAreaReady(int a){if(a==2)latestReady=true;else if(a==1)reviewReady=true;else ready=true;}
    public CompoundTag returns=new CompoundTag();
    public static WorkshopData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(WorkshopData::new,WorkshopData::load),"frontierstructures_workshop");}
    private static WorkshopData load(CompoundTag t,HolderLookup.Provider p){
        var d=new WorkshopData();d.floorCursor=t.getInt("floorCursor");d.placed=t.getInt("placed");d.ready=t.getBoolean("ready");d.returns=t.getCompound("returns");
        d.reviewFloorCursor=t.getInt("reviewFloorCursor");d.reviewPlaced=t.getInt("reviewPlaced");d.reviewReady=t.getBoolean("reviewReady");d.latestFloorCursor=t.getInt("latestFloorCursor");d.latestPlaced=t.getInt("latestPlaced");d.latestReady=t.getBoolean("latestReady");return d;
    }
    @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider p){
        t.putInt("floorCursor",floorCursor);t.putInt("placed",placed);t.putBoolean("ready",ready);t.put("returns",returns);
        t.putInt("reviewFloorCursor",reviewFloorCursor);t.putInt("reviewPlaced",reviewPlaced);t.putBoolean("reviewReady",reviewReady);t.putInt("latestFloorCursor",latestFloorCursor);t.putInt("latestPlaced",latestPlaced);t.putBoolean("latestReady",latestReady);return t;
    }
}
