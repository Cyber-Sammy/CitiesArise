package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import java.util.*;

/** Ordered, strictly repeated floor sections. Module catalogs live once on the owning composition. */
public record BuildingSections(List<Section> sections,String bottomCap,String topCap,String basement,String roof) {
    public BuildingSections(List<Section> sections,String bottomCap,String topCap){this(sections,bottomCap,topCap,"","");}
    public record Surface(String type,Set<String> accepts,int priority,boolean retain,int gap,List<Cell> gapCells) {
        public Surface {
            accepts=Set.copyOf(accepts);gapCells=List.copyOf(gapCells);
            // Reuse the boundary policy validation; concrete layer extents are derived from the floor mask.
            new ModuleBoundary("port",ModuleBoundary.Role.FLOOR,type,accepts,new Vec(0,0,0),new Vec(0,0,0),priority,retain,gap,gapCells);
        }
        ModuleBoundary boundary(String port,Vec size,boolean upper) {
            int y=upper?size.y()-1:0;
            return new ModuleBoundary(port,upper?ModuleBoundary.Role.CEILING:ModuleBoundary.Role.FLOOR,type,accepts,
                    new Vec(0,y,0),new Vec(size.x()-1,y,size.z()-1),priority,retain,gap,gapCells);
        }
    }
    public record Floor(List<String> roots,List<String> attachments,String entranceJoint,int minimumModules,
                        int maximumModules,FloorLayout layout,String down,String up,Surface floor,Surface ceiling) {
        public Floor {
            roots=List.copyOf(roots);attachments=List.copyOf(attachments);Objects.requireNonNull(layout);
            Objects.requireNonNull(down);Objects.requireNonNull(up);Objects.requireNonNull(floor);Objects.requireNonNull(ceiling);
            if(roots.isEmpty() || minimumModules<1 || maximumModules<minimumModules || maximumModules>16)
                throw new IllegalArgumentException("Invalid section floor budget");
            for(var port:layout.ports())if(port.face().ordinal()>=4 && !port.id().equals(down) && !port.id().equals(up))
                throw new IllegalArgumentException("Section floor has an unassigned vertical port");
            if(!down.isEmpty())requirePort(layout,down,Face.DOWN);
            if(!up.isEmpty())requirePort(layout,up,Face.UP);
        }
        static FloorLayout.Port requirePort(FloorLayout layout,String id,Face face) {
            var p=layout.ports().stream().filter(q->q.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown section port: "+id));
            if(p.face()!=face || !p.walkable())throw new IllegalArgumentException("Section connection must be a walkable vertical port");return p;
        }
        CompositionSettings settings(CompositionSettings owner) {
            return new CompositionSettings(owner.modules(),roots,attachments,entranceJoint,owner.requiredTags(),owner.forbiddenTags(),owner.exceptions(),
                    minimumModules,maximumModules,layout.size().y(),owner.damageSeverity(),owner.airMaterial(),owner.materialRules(),layout);
        }
    }
    public record Section(String id,int floors,Floor recipe) {
        public Section {Objects.requireNonNull(id);Objects.requireNonNull(recipe);
            if(id.isBlank() || id.length()>128 || floors<1 || floors>16)throw new IllegalArgumentException("Invalid building section");}
    }
    public BuildingSections {
        basement=basement==null?"":basement;roof=roof==null?"":roof;
        sections=List.copyOf(sections);Objects.requireNonNull(bottomCap);Objects.requireNonNull(topCap);
        if(sections.isEmpty() || sections.size()>8 || sections.stream().map(Section::id).distinct().count()!=sections.size()
                || sections.stream().mapToInt(Section::floors).sum()>16 || bottomCap.isBlank() || topCap.isBlank())
            throw new IllegalArgumentException("Invalid building sections or terminal caps");
        var base=sections.getFirst().recipe().layout();
        if(base.ports().stream().filter(p->p.id().equals(base.entrance())).findFirst().orElseThrow().face().ordinal()>=4)
            throw new IllegalArgumentException("Building ground floor requires a horizontal exterior entrance");
        for(int i=0;i<sections.size();i++) {
            var s=sections.get(i);var f=s.recipe();
            if((i>0 || s.floors()>1) && f.down().isEmpty() || (i<sections.size()-1 || s.floors()>1) && f.up().isEmpty())
                throw new IllegalArgumentException("Repeated/connected floors need both vertical ports");
        }
    }
    void validateModules(Map<String,ModuleDefinition> modules) {
        for(var id:List.of(basement,roof))if(!id.isEmpty() && !modules.containsKey(id))throw new IllegalArgumentException("Unknown building terminal module: "+id);
        if(!basement.isEmpty() && sections.getFirst().recipe().down().isEmpty() || !roof.isEmpty() && sections.getLast().recipe().up().isEmpty())
            throw new IllegalArgumentException("Terminal modules require matching section ports");
        for(var s:sections) {
            var f=s.recipe();f.layout().validateModules(modules,f.roots(),f.attachments());
            BuildingSectionAssembler.shell(f); // Validate concrete boundary extents and authored gap cells at reload.
            for(var id:f.roots())if(modules.get(id).joints().stream().noneMatch(j->j.id().equals(f.entranceJoint()) && f.layout().acceptsRootAccess(j)))
                throw new IllegalArgumentException("Section root needs its declared floor access");
            if(!f.layout().entrance().isEmpty() && f.layout().ports().stream().anyMatch(p->p.id().equals(f.layout().entrance()) && p.face()==Face.DOWN)
                    && !f.layout().entrance().equals(f.down()))
                throw new IllegalArgumentException("Upper floor access must use its assigned downward connection");
        }
    }
    public Set<String> moduleIds() {
        var ids=new HashSet<String>();for(var s:sections){ids.addAll(s.recipe().roots());ids.addAll(s.recipe().attachments());}
        for(var id:List.of(basement,roof))if(!id.isEmpty())ids.add(id);return Set.copyOf(ids);
    }
}
