package com.formaworks.frontierstructures;

import java.nio.file.*;
import java.util.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Explicit, bounded retrofit of already loaded terrain. Never runs just because a chunk loads. */
public final class ExistingTerrain {
    public record Site(String template,BlockPos origin,Vec3i size) {}
    private static final String[] TYPES={"trapper","fishing","homestead","smokehouse","lookout","lodge","outfitter"};
    private static final Map<UUID,Preview> PREVIEWS=new HashMap<>();
    private static final Map<MinecraftServer,Scan> SCANS=new IdentityHashMap<>();
    private record Preview(ServerLevel level,List<Site> sites,long expires){}
    private static class Scan {
        final ServerPlayer player;final int radius,cx,cz;final List<Site> found=new ArrayList<>();int index;
        Scan(ServerPlayer p,int r){player=p;radius=r;cx=p.blockPosition().getX();cz=p.blockPosition().getZ();}
    }
    public static final class Ledger extends SavedData {
        public CompoundTag sites=new CompoundTag();
        static Ledger load(CompoundTag t,HolderLookup.Provider provider){var d=new Ledger();d.sites=t.getCompound("sites");return d;}
        public static Ledger get(ServerLevel l){return l.getDataStorage().computeIfAbsent(new Factory<>(Ledger::new,Ledger::load),"frontierstructures_existing_terrain");}
        @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider provider){t.put("sites",sites);return t;}
    }
    public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("frontierstructures").requires(s->s.hasPermission(2))
            .then(Commands.literal("populate")
                .then(Commands.literal("preview").executes(c->preview(c.getSource().getPlayerOrException(),128))
                    .then(Commands.argument("radius",IntegerArgumentType.integer(64,512)).executes(c->preview(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"radius")))))
                .then(Commands.literal("apply").executes(c->apply(c.getSource().getPlayerOrException())))
                .then(Commands.literal("undo").executes(c->undoLast(c.getSource().getPlayerOrException())))
                .then(Commands.literal("cancel").executes(c->{var p=c.getSource().getPlayerOrException();PREVIEWS.remove(p.getUUID());var scan=SCANS.get(p.getServer());if(scan!=null&&scan.player==p)SCANS.remove(p.getServer());tell(p,"Pending preview cancelled.");return 1;}))));
    }
    private static void tell(ServerPlayer p,String s){p.sendSystemMessage(Component.literal("[Frontier Structures] "+s));}
    private static boolean allowed(ServerPlayer p){return p.isCreative()&&p.serverLevel().dimension().equals(Level.OVERWORLD);}
    private static int preview(ServerPlayer p,int radius){
        if(!allowed(p)){tell(p,"Use Creative mode in the Overworld for existing-terrain placement.");return 0;}
        if(SCANS.containsKey(p.getServer())){tell(p,"A terrain scan is already running.");return 0;}
        PREVIEWS.remove(p.getUUID());SCANS.put(p.getServer(),new Scan(p,radius));
        tell(p,"Checking loaded terrain only. No blocks change during preview. Built blocks, containers, trees and steep/wet sites are excluded.");return 1;
    }
    public static void tick(ServerTickEvent.Post e){
        var scan=SCANS.get(e.getServer());if(scan==null||e.getServer().getTickCount()%4!=0)return;
        var p=scan.player;if(p.hasDisconnected()||!allowed(p)){SCANS.remove(e.getServer());return;}
        int side=scan.radius/32*2+1,total=side*side;
        for(int attempts=0;attempts<2&&scan.index<total&&scan.found.size()<3;attempts++){
            int n=scan.index++,x=scan.cx+(n%side-side/2)*32,z=scan.cz+(n/side-side/2)*32;
            if((long)(x-scan.cx)*(x-scan.cx)+(long)(z-scan.cz)*(z-scan.cz)<48*48)continue;
            String id="settlement_"+TYPES[Math.floorMod(Math.floorDiv(x,32)*31+Math.floorDiv(z,32)*17,TYPES.length)];
            var candidate=find(p.serverLevel(),id,x,z);
            if(candidate!=null&&scan.found.stream().allMatch(s->s.origin.distSqr(candidate.origin)>128*128))scan.found.add(candidate);
        }
        if(scan.index>=total||scan.found.size()==3){
            SCANS.remove(e.getServer());PREVIEWS.put(p.getUUID(),new Preview(p.serverLevel(),List.copyOf(scan.found),p.serverLevel().getGameTime()+12000));
            if(scan.found.isEmpty())tell(p,"No suitable loaded clearing found. Move to open, gently sloping land and preview again. No chunks were generated or changed.");
            else {for(var s:scan.found)tell(p,s.template+" at "+s.origin.toShortString()+" ("+s.size.getX()+" x "+s.size.getZ()+" blocks)");tell(p,"Review these sites, then /frontierstructures populate apply. Preview expires in 10 minutes; /populate undo restores the last batch if it has not been edited.");}
        }
    }
    private static boolean loaded(ServerLevel l,int x,int z,int sx,int sz){
        for(int cx=(x-4)>>4;cx<=(x+sx+3)>>4;cx++)for(int cz=(z-4)>>4;cz<=(z+sz+3)>>4;cz++)if(l.getChunkSource().getChunkNow(cx,cz)==null)return false;
        return true;
    }
    private static final net.minecraft.tags.TagKey<Block> GROUND=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,FrontierStructures.id("retrofit_ground"));
    private static final net.minecraft.tags.TagKey<Block> PLANTS=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,FrontierStructures.id("retrofit_plants"));
    private static boolean ground(net.minecraft.world.level.block.state.BlockState s){
        return s.is(GROUND)||s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.DIRT)||s.is(Blocks.COARSE_DIRT)||s.is(Blocks.PODZOL)||s.is(Blocks.ROOTED_DIRT)||s.is(Blocks.STONE)||s.is(Blocks.ANDESITE)||s.is(Blocks.GRANITE)||s.is(Blocks.DIORITE)||s.is(Blocks.GRAVEL)||s.is(Blocks.SAND)||s.is(Blocks.CLAY)||s.is(Blocks.MOSS_BLOCK);
    }
    private static boolean plant(net.minecraft.world.level.block.state.BlockState s){
        return s.is(PLANTS)||s.isAir()||s.is(Blocks.SHORT_GRASS)||s.is(Blocks.TALL_GRASS)||s.is(Blocks.FERN)||s.is(Blocks.LARGE_FERN)||s.is(Blocks.DEAD_BUSH)||s.is(Blocks.DANDELION)||s.is(Blocks.POPPY)||s.is(Blocks.SNOW);
    }
    public static Site find(ServerLevel l,String name,int x,int z){
        var t=l.getStructureManager().get(FrontierStructures.id("expedition/"+name)).orElse(null);if(t==null)return null;
        var size=t.getSize();if(size.getX()>48||size.getZ()>48||!loaded(l,x,z,size.getX(),size.getZ()))return null;
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for(int dx=0;dx<size.getX();dx++)for(int dz=0;dz<size.getZ();dz++){
            int y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x+dx,z+dz);
            while(y>l.getMinBuildHeight()+3&&plant(l.getBlockState(new BlockPos(x+dx,y-1,z+dz))))y--;
            if(!ground(l.getBlockState(new BlockPos(x+dx,y-1,z+dz))))return null;
            low=Math.min(low,y);high=Math.max(high,y);if(high-low>2)return null;
        }
        var site=new Site(name,new BlockPos(x,high-2,z),size);
        if(high+size.getY()+2>=l.getMaxBuildHeight()||low<l.getMinBuildHeight()+2)return null;
        var ledger=Ledger.get(l);
        for(String key:ledger.sites.getAllKeys()){
            var old=ledger.sites.getCompound(key);var at=BlockPos.of(old.getLong("origin"));
            if(at.distSqr(site.origin)<128*128)return null;
        }
        return safe(l,site)?site:null;
    }
    public static boolean safe(ServerLevel l,Site site){
        var o=site.origin;var size=site.size;if(!loaded(l,o.getX(),o.getZ(),size.getX(),size.getZ()))return false;
        // Deliberately conservative allow-list. Modded ground is skipped unless explicitly supported.
        for(int dx=-4;dx<size.getX()+4;dx++)for(int dz=-4;dz<size.getZ()+4;dz++)for(int dy=-3;dy<size.getY()+2;dy++){
            var at=o.offset(dx,dy,dz);var s=l.getBlockState(at);
            if(l.getBlockEntity(at)!=null||(!plant(s)&&!(dy<2&&ground(s))&&!(dy<0&&s.is(Blocks.BEDROCK))))return false;
        }
        return l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,new net.minecraft.world.phys.AABB(o.getX()-4,o.getY()-3,o.getZ()-4,o.getX()+size.getX()+4,o.getY()+size.getY()+2,o.getZ()+size.getZ()+4)).isEmpty();
    }
    private static Path backupRoot(ServerLevel l)throws java.io.IOException{
        var root=l.getServer().getWorldPath(LevelResource.ROOT).resolve("generated/frontierstructures/terrain-backups");Files.createDirectories(root);return root;
    }
    public static String place(ServerLevel l,Site site)throws java.io.IOException{
        if(!safe(l,site))throw new IllegalStateException("Site changed or is no longer safe; preview again.");
        var t=l.getStructureManager().get(FrontierStructures.id("expedition/"+site.template)).orElseThrow();
        String id=UUID.randomUUID().toString();var old=new StructureTemplate();old.fillFromWorld(l,site.origin,site.size,false,Blocks.STRUCTURE_VOID);
        NbtIo.writeCompressed(old.save(new CompoundTag()),backupRoot(l).resolve(id+"-before.nbt"));
        try{
            if(!t.placeInWorld(l,site.origin,site.origin,new StructurePlaceSettings().setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING),l.random,2))throw new IllegalStateException("Placement failed");
            var after=new StructureTemplate();after.fillFromWorld(l,site.origin,site.size,false,Blocks.STRUCTURE_VOID);
            NbtIo.writeCompressed(after.save(new CompoundTag()),backupRoot(l).resolve(id+"-after.nbt"));
        }catch(Exception ex){old.placeInWorld(l,site.origin,site.origin,new StructurePlaceSettings().setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING),l.random,2);throw ex;}
        var record=new CompoundTag();record.putLong("origin",site.origin.asLong());record.putString("template",site.template);record.putInt("sx",site.size.getX());record.putInt("sy",site.size.getY());record.putInt("sz",site.size.getZ());
        Ledger.get(l).sites.put(id,record);Ledger.get(l).setDirty();return id;
    }
    private static int apply(ServerPlayer p){
        var preview=PREVIEWS.remove(p.getUUID());if(!allowed(p)||preview==null||preview.level!=p.serverLevel()||preview.expires<p.serverLevel().getGameTime()){tell(p,"Run /frontierstructures populate preview first.");return 0;}
        int count=0;var ids=new ListTag();
        for(var site:preview.sites){
            // Recheck terrain height as well as blocks; old previews cannot place over newly dug land.
            var fresh=find(p.serverLevel(),site.template,site.origin.getX(),site.origin.getZ());
            if(fresh==null||!fresh.origin.equals(site.origin)){tell(p,"Skipped changed site at "+site.origin.toShortString());continue;}
            try{ids.add(StringTag.valueOf(place(p.serverLevel(),site)));count++;}catch(Exception ex){tell(p,"Placement stopped: "+ex.getMessage());break;}
        }
        if(count>0){p.getPersistentData().put("frontierstructuresLastTerrain",ids);p.getPersistentData().putString("frontierstructuresLastDimension",p.level().dimension().location().toString());}
        tell(p,"Placed "+count+" buildings with terrain backups. /frontierstructures populate undo can restore this batch before you edit it.");return count;
    }
    public static boolean undo(ServerLevel l,String id)throws java.io.IOException{
        var ledger=Ledger.get(l);if(!ledger.sites.contains(id))return false;var record=ledger.sites.getCompound(id);var o=BlockPos.of(record.getLong("origin"));var size=new Vec3i(record.getInt("sx"),record.getInt("sy"),record.getInt("sz"));
        if(!loaded(l,o.getX(),o.getZ(),size.getX(),size.getZ()))return false;
        var expected=NbtIo.readCompressed(backupRoot(l).resolve(id+"-after.nbt"),NbtAccounter.unlimitedHeap());var now=new StructureTemplate();now.fillFromWorld(l,o,size,false,Blocks.STRUCTURE_VOID);
        if(!expected.equals(now.save(new CompoundTag())))return false;
        var before=NbtIo.readCompressed(backupRoot(l).resolve(id+"-before.nbt"),NbtAccounter.unlimitedHeap());
        var t=l.getStructureManager().readStructure(before);if(!t.placeInWorld(l,o,o,new StructurePlaceSettings().setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING),l.random,2))return false;
        ledger.sites.remove(id);ledger.setDirty();return true;
    }
    private static int undoLast(ServerPlayer p){
        if(!allowed(p)||!p.level().dimension().location().toString().equals(p.getPersistentData().getString("frontierstructuresLastDimension")))return 0;
        var ids=p.getPersistentData().getList("frontierstructuresLastTerrain",Tag.TAG_STRING);var remaining=new ListTag();int restored=0;
        for(int i=0;i<ids.size();i++){String id=ids.getString(i);try{if(undo(p.serverLevel(),id))restored++;else remaining.add(StringTag.valueOf(id));}catch(Exception ex){remaining.add(StringTag.valueOf(id));tell(p,"Undo stopped: "+ex.getMessage());}}
        p.getPersistentData().put("frontierstructuresLastTerrain",remaining);tell(p,"Restored "+restored+" sites. "+remaining.size()+" skipped because unloaded or changed since placement; your edits were preserved.");return restored;
    }
    public static void stopped(ServerStoppedEvent e){SCANS.remove(e.getServer());PREVIEWS.entrySet().removeIf(v->v.getValue().level.getServer()==e.getServer());}
}
