package com.formaworks.frontierstructures;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** All mutations run on the server thread. No changes to the player's game settings. */
public final class Workshop {
    public static final ResourceKey<Level> DIMENSION=ResourceKey.create(Registries.DIMENSION,FrontierStructures.id("workshop"));
    public record Plot(String name,String title,int x,int z,int width,int depth) {
        public BlockPos origin(){return new BlockPos(x,62,z);}
    }
    public static final List<Plot> PLOTS=List.of(
        new Plot("lodge","Hunting lodge",0,0,44,48),new Plot("outfitter","Outfitter",48,0,44,48),
        new Plot("trapper","Trapper cabin",96,0,44,48),new Plot("homestead","Homestead",144,0,44,48),
        new Plot("lookout","Watchtower",192,0,44,48),new Plot("smokehouse","Smokehouse",240,0,44,48),
        new Plot("fishing","Fishing cabin",288,0,44,48),new Plot("hamlet","Whole hamlet",0,64,144,96));
    private static final int WIDTH=352,DEPTH=176,FLOOR_TOTAL=WIDTH*DEPTH*2;
    public static final int REVIEW_Z=224;
    public static final int LATEST_Z=448;
    public static final List<Plot> LATEST_PLOTS;
    static {LATEST_PLOTS=PLOTS;}
    private static Plot areaPlot(Plot p,int area){return new Plot(p.name,p.title,p.x,p.z+area*224,p.width,p.depth);}
    public static Plot reviewPlot(Plot p){return new Plot(p.name,p.title,p.x,p.z+REVIEW_Z,p.width,p.depth);}
    private static final Map<MinecraftServer,Job> JOBS=new IdentityHashMap<>();
    private static class Job {
        final UUID player;final boolean save;final int area;int index;Path export;final List<Map<String,Object>> manifest=new ArrayList<>();
        Job(UUID p,boolean s,int a){player=p;save=s;area=a;}
        List<Plot> plots(){return area==2?LATEST_PLOTS:PLOTS;}
    }
    public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("frontierstructures").requires(s->s.hasPermission(2))
            .then(Commands.literal("workshop").executes(c->begin(c.getSource().getPlayerOrException(),false,false)))
            .then(Commands.literal("copyall").executes(c->begin(c.getSource().getPlayerOrException(),false,false)))
            .then(Commands.literal("review").executes(c->begin(c.getSource().getPlayerOrException(),false,true)))
            .then(Commands.literal("latest").executes(c->beginArea(c.getSource().getPlayerOrException(),false,2)))
            .then(Commands.literal("save").executes(c->{var p=c.getSource().getPlayerOrException();return beginArea(p,true,p.level().dimension().equals(DIMENSION)?(p.getZ()>=LATEST_Z-16?2:p.getZ()>=REVIEW_Z-16?1:0):0);})
                .then(Commands.literal("latest").executes(c->beginArea(c.getSource().getPlayerOrException(),true,2)))
                .then(Commands.literal("original").executes(c->begin(c.getSource().getPlayerOrException(),true,false)))
                .then(Commands.literal("review").executes(c->begin(c.getSource().getPlayerOrException(),true,true))))
            .then(Commands.literal("return").executes(c->leave(c.getSource().getPlayerOrException()))));
    }
    private static void tell(ServerPlayer p,String text){if(p!=null)p.sendSystemMessage(Component.literal("[Frontier Structures] "+text));}
    private static int begin(ServerPlayer player,boolean save,boolean review){
        return beginArea(player,save,review?1:0);
    }
    private static int beginArea(ServerPlayer player,boolean save,int area){
        var server=player.getServer();var data=WorkshopData.get(server);
        if(!player.isCreative()){tell(player,"Switch to Creative mode first: /gamemode creative");return 0;}
        if(server.getLevel(DIMENSION)==null){tell(player,"The workshop dimension is unavailable. Restart the world with this addon enabled.");return 0;}
        if(JOBS.containsKey(server)){tell(player,"The current workshop job is still running. Please wait.");return 0;}
        boolean ready=data.areaReady(area);
        if(save&&!ready){tell(player,"Create this area first with /frontierstructures "+(area==2?"latest":area==1?"review":"copyall"));return 0;}
        if(!save&&ready){enter(player,area);return 1;}
        var job=new Job(player.getUUID(),save,area);
        if(save){
            try {
                var root=server.getWorldPath(LevelResource.ROOT).resolve("generated/frontierstructures/exports");Files.createDirectories(root);
                job.export=Files.createDirectory(root.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+"-"+UUID.randomUUID().toString().substring(0,8)));
            }catch(IOException e){tell(player,"Could not create the export folder: "+e.getMessage());return 0;}
        }
        JOBS.put(server,job);
        tell(player,save?"Saving "+job.plots().size()+" plots. Please pause building until the save finishes.":"Building the separate editing area with "+job.plots().size()+" plots. Repeating this command preserves edits.");
        return 1;
    }
    public static boolean busy(MinecraftServer s){return JOBS.containsKey(s);}
    public static void stopped(ServerStoppedEvent e){JOBS.remove(e.getServer());}
    public static void tick(ServerTickEvent.Post event){
        var server=event.getServer();var job=JOBS.get(server);if(job==null)return;
        var player=server.getPlayerList().getPlayer(job.player);var level=server.getLevel(DIMENSION);var data=WorkshopData.get(server);
        try {
            if(job.save){
                var plot=job.plots().get(job.index);export(level,areaPlot(plot,job.area),job);job.index++;
                if(job.index==job.plots().size()){
                    Files.writeString(job.export.resolve("manifest.json"),new GsonBuilder().setPrettyPrinting().create().toJson(job.manifest));
                    JOBS.remove(server);tell(player,"Saved "+job.plots().size()+" plots to "+job.export.toAbsolutePath()+". Tell Codex your edits are ready to package.");
                    System.out.println("FRONTIER_STRUCTURES_EXPORT "+job.export.toAbsolutePath());
                }
                return;
            }
            int cursor=data.areaCursor(job.area);
            int placed=data.areaPlaced(job.area);
            if(cursor<FLOOR_TOTAL){
                int end=Math.min(FLOOR_TOTAL,cursor+2048);
                for(int i=cursor;i<end;i++){
                    int layer=i/(WIDTH*DEPTH),cell=i%(WIDTH*DEPTH),x=cell%WIDTH-8,z=cell/WIDTH-8+(job.area*224);
                    level.setBlock(new BlockPos(x,62+layer,z),FrontierStructures.FLOOR.get().defaultBlockState(),2);
                }
                data.setAreaCursor(job.area,end);data.setDirty();return;
            }
            if(placed<job.plots().size()){
                var plot=areaPlot(job.plots().get(placed),job.area);var id=FrontierStructures.id("expedition/settlement_"+plot.name);
                var template=server.getStructureManager().get(id).orElseThrow(()->new IllegalStateException("Missing template "+id));
                var settings=new StructurePlaceSettings().setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
                if(!template.placeInWorld(level,plot.origin(),plot.origin(),settings,level.random,2))throw new IllegalStateException("Could not place "+id);
                markPlot(level,plot);data.setAreaPlaced(job.area,placed+1);data.setDirty();tell(player,"Placed "+plot.title+" ("+(placed+1)+"/"+job.plots().size()+")");return;
            }
            data.setAreaReady(job.area);data.setDirty();JOBS.remove(server);if(player!=null)enter(player,job.area);
        }catch(Exception e){JOBS.remove(server);tell(player,"Workshop job stopped safely: "+e.getMessage());e.printStackTrace();}
    }
    private static void markPlot(ServerLevel l,Plot plot){
        // Markers sit outside capture bounds and cannot accidentally become part of an exported building.
        for(int x=plot.x-1;x<=plot.x+plot.width;x++)for(int z:new int[]{plot.z-1,plot.z+plot.depth})l.setBlock(new BlockPos(x,63,z),Blocks.POLISHED_ANDESITE.defaultBlockState(),2);
        for(int z=plot.z;z<plot.z+plot.depth;z++)for(int x:new int[]{plot.x-1,plot.x+plot.width})l.setBlock(new BlockPos(x,63,z),Blocks.POLISHED_ANDESITE.defaultBlockState(),2);
        var pos=new BlockPos(plot.x+1,64,plot.z-2);l.setBlock(pos,Blocks.SPRUCE_SIGN.defaultBlockState(),3);
        if(l.getBlockEntity(pos) instanceof SignBlockEntity sign){
            sign.updateText(t->t.setMessage(0,Component.literal(plot.title)).setMessage(1,Component.literal("Edit inside border")).setMessage(2,Component.literal("Save: /frontierstructures")).setMessage(3,Component.literal("save")),true);sign.setChanged();
        }
    }
    private static void enter(ServerPlayer p,int area){
        var d=WorkshopData.get(p.getServer());
        if(!p.level().dimension().equals(DIMENSION)){
            var t=new CompoundTag();t.putString("dimension",p.level().dimension().location().toString());t.putDouble("x",p.getX());t.putDouble("y",p.getY());t.putDouble("z",p.getZ());t.putFloat("yaw",p.getYRot());t.putFloat("pitch",p.getXRot());d.returns.put(p.getStringUUID(),t);d.setDirty();
        }
        p.teleportTo(p.getServer().getLevel(DIMENSION),12,64,-5+(area*224),0,0);
        tell(p,(area==2?"1.2 latest area":area==1?"1.1 review area":"Original workshop")+" ready. Edit inside each border, Y 62–125. /frontierstructures save exports this area's plots; /frontierstructures return takes you back. Your edits persist here.");
    }
    private static int leave(ServerPlayer p){
        var d=WorkshopData.get(p.getServer());var t=d.returns.getCompound(p.getStringUUID());
        if(!p.level().dimension().equals(DIMENSION)||t.isEmpty()){tell(p,"No workshop return point is recorded for you.");return 0;}
        var id=ResourceLocation.tryParse(t.getString("dimension"));var target=id==null?null:p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,id));
        if(target==null){tell(p,"Your original dimension is unavailable; your return point was kept.");return 0;}
        p.teleportTo(target,t.getDouble("x"),t.getDouble("y"),t.getDouble("z"),t.getFloat("yaw"),t.getFloat("pitch"));d.returns.remove(p.getStringUUID());d.setDirty();return 1;
    }
    private static void export(ServerLevel level,Plot plot,Job job)throws IOException {
        int minX=plot.width,minZ=plot.depth,maxX=-1,maxZ=-1,maxY=-1;
        for(int y=0;y<64;y++)for(int z=0;z<plot.depth;z++)for(int x=0;x<plot.width;x++){
            var s=level.getBlockState(plot.origin().offset(x,y,z));
            if(s.isAir()||s.is(FrontierStructures.FLOOR)||s.is(FrontierStructures.SPACE))continue;
            minX=Math.min(minX,x);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxZ=Math.max(maxZ,z);maxY=Math.max(maxY,y);
        }
        if(maxY<0)throw new IOException("The "+plot.title+" plot is empty; previous exports are untouched.");
        var start=plot.origin().offset(minX,0,minZ);var size=new Vec3i(maxX-minX+1,Math.min(64,maxY+2),maxZ-minZ+1);
        var template=new StructureTemplate();template.fillFromWorld(level,start,size,false,Blocks.STRUCTURE_VOID);
        var tag=template.save(new CompoundTag());var palette=tag.getList("palette",Tag.TAG_COMPOUND);
        var excluded=new HashSet<Integer>();
        for(int i=0;i<palette.size();i++){
            var state=palette.getCompound(i);String name=state.getString("Name");
            if(name.equals("frontierstructures:workshop_floor"))excluded.add(i);
            if(name.equals("minecraft:air")||name.equals("minecraft:cave_air")||name.equals("minecraft:void_air"))state.putString("Name","frontierstructures:structure_space");
        }
        var blocks=tag.getList("blocks",Tag.TAG_COMPOUND);blocks.removeIf(b->excluded.contains(((CompoundTag)b).getInt("state")));
        String filename="settlement_"+plot.name+".nbt";NbtIo.writeCompressed(tag,job.export.resolve(filename));
        job.manifest.add(Map.of("name","settlement_"+plot.name,"file",filename,"origin",List.of(start.getX(),start.getY(),start.getZ()),"size",List.of(size.getX(),size.getY(),size.getZ()),"entitiesIncluded",false));
    }
}
