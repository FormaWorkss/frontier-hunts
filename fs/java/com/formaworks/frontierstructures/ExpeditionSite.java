package com.formaworks.frontierstructures;
import com.mojang.serialization.MapCodec;import com.mojang.serialization.codecs.RecordCodecBuilder;import java.util.Optional;import net.minecraft.core.BlockPos;import net.minecraft.nbt.CompoundTag;import net.minecraft.resources.ResourceLocation;import net.minecraft.util.RandomSource;import net.minecraft.world.level.ServerLevelAccessor;import net.minecraft.world.level.block.Rotation;import net.minecraft.world.level.levelgen.structure.*;import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;import net.minecraft.world.level.levelgen.structure.templatesystem.*;
public final class ExpeditionSite extends Structure {
 public static final MapCodec<ExpeditionSite> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),ResourceLocation.CODEC.fieldOf("template").forGetter(s->s.template),
  com.mojang.serialization.Codec.INT.optionalFieldOf("max_relief",11).forGetter(s->s.maxRelief)).apply(i,ExpeditionSite::new));
 private final ResourceLocation template;private final int maxRelief;
 public ExpeditionSite(StructureSettings settings,ResourceLocation template,int maxRelief){super(settings);this.template=template;this.maxRelief=maxRelief;}
 /** template y of the yard's first air layer (y 0..1 are the authored ground and foundations) */
 static final int GROUND_AIR=2;
 // [structures2] The old check sampled a 7 x 7 grid with getBaseColumn (a full 384-block column, plus slope sampling, per
 // point on the alpine generator) and demanded <= 2 blocks of relief over the building plus a 6-block apron. On Frontier
 // terrain virtually every candidate failed, so the buildings never generated, and /locate hung the server (watchdog).
 // Now: cheap height samples (McTerrain: one alpine layout sample per point, cached), the building is fitted to the
 // ground by TerrainFit (cut / fill / graded apron), so a few blocks of relief are fine.
 @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext c){
  var building=c.structureTemplateManager().getOrCreate(template);var size=building.getSize();if(size.getX()==0||size.getZ()==0)return Optional.empty();
  Rotation rotation=Rotation.getRandom(c.random());int x=c.chunkPos().getMinBlockX()+c.random().nextInt(8),z=c.chunkPos().getMinBlockZ()+c.random().nextInt(8);
  var t=new Heights(c.chunkGenerator(),c.heightAccessor(),c.randomState());
  if(!template.getPath().contains("lookout"))return at(c,building,size,rotation,x,z,t);
  // [gear20] a lookout tower stands where it can see: try a 5 x 5 grid of spots around the chunk (24 blocks apart) and
  // build on the most commanding one - a knoll, ridge or bench at least 3 blocks above the ground 48 blocks around
  // it - or not at all. Before, it went on whatever spot the dice picked, often a hollow, and so read as random.
  int cx=c.chunkPos().getMiddleBlockX(),cz=c.chunkPos().getMiddleBlockZ();
  java.util.List<int[]> spots=new java.util.ArrayList<>();
  for(int i=-2;i<=2;i++)for(int j=-2;j<=2;j++){int sx=cx+i*24,sz=cz+j*24;int f=t.floor(sx,sz);if(t.surface(sx,sz)>f)continue;
   int sum=0,higher=0;for(int k=0;k<12;k++){double a=k*Math.PI/6;int g=t.floor(sx+(int)Math.round(Math.cos(a)*48),sz+(int)Math.round(Math.sin(a)*48));sum+=g;if(g>f+2)higher++;}
   int prominence=f-sum/12;if(prominence>=3&&higher<=3)spots.add(new int[]{sx,sz,prominence});}
  spots.sort((a,b)->Integer.compare(b[2],a[2]));
  for(int[] sp:spots){var r=at(c,building,size,rotation,sp[0]-size.getX()/2,sp[1]-size.getZ()/2,t);if(r.isPresent())return r;}
  return Optional.empty();
 }
 private Optional<GenerationStub> at(GenerationContext c,StructureTemplate building,net.minecraft.core.Vec3i size,Rotation rotation,int x,int z,Heights t){
  var bounds=building.getBoundingBox(Piece.settings(rotation),new BlockPos(x,0,z));
  // cheap first look (centre + corners) rejects most candidates before the full grid
  {int lo=Integer.MAX_VALUE,hi=Integer.MIN_VALUE;
   for(int[] q:new int[][]{{0,0},{1,0},{0,1},{1,1},{-1,-1}}){int qx=q[0]<0?(bounds.minX()+bounds.maxX())/2:q[0]==0?bounds.minX():bounds.maxX(),qz=q[1]<0?(bounds.minZ()+bounds.maxZ())/2:q[1]==0?bounds.minZ():bounds.maxZ();
    int f=t.floor(qx,qz);if(q[0]<0&&t.surface(qx,qz)>f)return Optional.empty();lo=Math.min(lo,f);hi=Math.max(hi,f);if(hi-lo>maxRelief)return Optional.empty();}}
  int n=Math.max(5,Math.min(8,Math.max(bounds.getXSpan(),bounds.getZSpan())/6));
  int[] hs=new int[n*n];int k=0,wet=0;
  for(int ix=0;ix<n;ix++)for(int iz=0;iz<n;iz++){int dx=bounds.minX()+(bounds.getXSpan()-1)*ix/(n-1),dz=bounds.minZ()+(bounds.getZSpan()-1)*iz/(n-1);
   int f=t.floor(dx,dz);if(t.surface(dx,dz)>f)wet++;hs[k++]=f;}
  // a pond or creek edge is fine for the fishing hut; open water under the building is not
  if(wet>(template.getPath().contains("fishing")?n:0))return Optional.empty();
  java.util.Arrays.sort(hs);int low=hs[0],high=hs[hs.length-1];if(high-low>maxRelief)return Optional.empty();
  // yard level: upper median (cut a little rather than build up on fill)
  int ground=hs[hs.length*3/5];
  if(ground<=c.chunkGenerator().getSeaLevel()-1||ground+size.getY()+4>=c.heightAccessor().getMaxBuildHeight())return Optional.empty();
  // [gear.19] never half a building next to land generated on an older version (see OldLand)
  if(OldLand.touches(bounds.minX(),bounds.minZ(),bounds.maxX(),bounds.maxZ(),TerrainFit.APRON+1))return Optional.empty();
  BlockPos pos=new BlockPos(x,ground-GROUND_AIR,z);
  return Optional.of(new GenerationStub(pos,b->b.addPiece(new Piece(c.structureTemplateManager(),pos,rotation,template))));
 }
 @Override public StructureType<?> type(){return FrontierStructures.SITE.get();}
 public static final class Piece extends TemplateStructurePiece {
  private java.util.List<BlockPos> clearance;
  private TerrainFit fit;
  public Piece(StructureTemplateManager m,BlockPos p,Rotation r,ResourceLocation id){super(FrontierStructures.PIECE.get(),0,m,id,id.toString(),settings(r),p);
   // [structures2] the graded apron lies outside the template box: let the neighbouring chunks run this piece too
   this.boundingBox=this.boundingBox.inflatedBy(TerrainFit.APRON+1);}
  public Piece(StructureTemplateManager m,CompoundTag t){super(FrontierStructures.PIECE.get(),t,m,id->settings(Rotation.valueOf(t.getString("Rotation"))));}
  static StructurePlaceSettings settings(Rotation r){return new StructurePlaceSettings().setRotation(r).setRotationPivot(new BlockPos(8,0,8)).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);}
  @Override public void postProcess(net.minecraft.world.level.WorldGenLevel level,net.minecraft.world.level.StructureManager structures,net.minecraft.world.level.chunk.ChunkGenerator generator,RandomSource random,BoundingBox box,net.minecraft.world.level.ChunkPos chunk,BlockPos pos){
   if(clearance==null)clearance=SettlementClearance.authored(templateName)?SettlementClearance.footprint(template):java.util.List.of();
   try{
    if(fit==null)fit=new TerrainFit(template,templatePosition,placeSettings,templatePosition.getY()+GROUND_AIR);
    var t=new Heights(generator,level,level.getLevel().getChunkSource().randomState());
    fit.apply(level,box,t);
   }catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger("frontierstructures").error("[Frontier Structures] terrain fit failed for {} at {}",templateName,templatePosition,ex);}
   SettlementClearance.clear(level,clearance,templatePosition,placeSettings,box);
   super.postProcess(level,structures,generator,random,box,chunk,pos);
   if(fit!=null)fit.dropOrphanBlockEntities(level,box);
  }
  @Override protected void addAdditionalSaveData(StructurePieceSerializationContext c,CompoundTag t){super.addAdditionalSaveData(c,t);t.putString("Rotation",placeSettings.getRotation().name());}
  @Override protected void handleDataMarker(String s,BlockPos p,ServerLevelAccessor l,RandomSource r,BoundingBox b){}
 }
}
