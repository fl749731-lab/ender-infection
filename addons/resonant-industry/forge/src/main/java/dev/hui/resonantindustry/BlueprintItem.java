package dev.hui.resonantindustry;

import dev.hui.resonantindustry.api.IndustryApi;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.network.chat.Component;

/** Other addons can register this item with their own IndustryApi machine definition. */
public final class BlueprintItem extends Item {
    public final String machine;
    public BlueprintItem(String machine){super(new Properties().stacksTo(1));IndustryApi.checkId(machine);this.machine=machine;}
    @Override public InteractionResult useOn(UseOnContext c){
        if(!(c.getLevel().getBlockEntity(c.getClickedPos()) instanceof IndustryNode n)||!n.role().equals("controller"))return InteractionResult.PASS;
        if(c.getLevel().isClientSide)return InteractionResult.SUCCESS;
        boolean ok=n.configure(machine);
        if(c.getPlayer()!=null)c.getPlayer().displayClientMessage(Component.translatable(ok?"message.resonant_industry.configured":"message.resonant_industry.configure_busy"),true);
        return ok?InteractionResult.CONSUME:InteractionResult.FAIL;
    }
}
