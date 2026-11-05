package dev.hui.resonantindustry.test;
import com.google.gson.*;
import dev.hui.resonantindustry.api.*;
import java.nio.file.*;
import java.util.Comparator;
public final class IndustryBlueprints {
    public static void write(Path path) throws java.io.IOException {
        var root=new JsonObject();root.addProperty("format",1);var machines=new JsonArray();
        for(var m:IndustryApi.machines().values()){
            var j=new JsonObject();j.addProperty("id",m.id());j.addProperty("width",m.width());j.addProperty("height",m.height());j.addProperty("length",m.length());var cells=new JsonArray();
            m.cells().entrySet().stream().sorted(Comparator.comparingInt((java.util.Map.Entry<MachineDefinition.Cell,String> e)->e.getKey().y()).thenComparingInt(e->e.getKey().z()).thenComparingInt(e->e.getKey().x())).forEach(e->{var c=new JsonObject();c.addProperty("x",e.getKey().x());c.addProperty("y",e.getKey().y());c.addProperty("z",e.getKey().z());c.addProperty("role",e.getValue());cells.add(c);});j.add("cells",cells);machines.add(j);
        }
        root.add("machines",machines);Files.createDirectories(path.toAbsolutePath().getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(root));
    }
    private IndustryBlueprints(){}
}
