package com.zurrtum.create.content.redstone.displayLink.target;

import com.zurrtum.create.api.behaviour.display.DisplayHolder;
import com.zurrtum.create.api.behaviour.display.DisplayTarget;
import com.zurrtum.create.catnip.data.Couple;
import com.zurrtum.create.content.redstone.displayLink.DisplayLinkContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;

import java.util.ArrayList;
import java.util.List;

public class SignDisplayTarget extends DisplayTarget {

    @Override
    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        BlockEntity be = context.getTargetBlockEntity();
        if (!(be instanceof SignBlockEntity sign)) {
            return;
        }

        boolean changed = false;
        Couple<SignText> signText = Couple.createWithContext(front -> sign.getText(slot(front)));
        DisplayHolder holder = (DisplayHolder) sign;
        for (int i = 0; i < text.size() && i + line < 4; i++) {
            if (i == 0) {
                reserve(line, holder, context);
            }
            if (i > 0 && isReserved(i + line, holder, context)) {
                break;
            }

            final int iFinal = i;
            String content = text.get(iFinal).getString(sign.getMaxTextLineWidth());
            signText = signText.map(st -> withLine(st, iFinal + line, Component.literal(content)));
            changed = true;
        }

        if (changed) {
            signText.forEachWithContext((st, front) -> sign.setText(st, slot(front)));
            context.level().sendBlockUpdated(context.getTargetPos(), sign.getBlockState(), sign.getBlockState(), 2);
        }
    }

    private static SignTextSlot slot(boolean front) {
        return front ? SignTextSlot.FRONT : SignTextSlot.BACK;
    }

    // SignText#setMessage was removed in 26.3; SignText.Mutable would also overwrite the filtered lines
    private static SignText withLine(SignText text, int index, Component message) {
        List<Component> messages = new ArrayList<>(text.getMessages(false));
        List<Component> filtered = new ArrayList<>(text.getMessages(true));
        messages.set(index, message);
        filtered.set(index, message);
        return new SignText(messages, filtered, text.getColor(), text.hasGlowingText());
    }

    @Override
    public DisplayTargetStats provideStats(DisplayLinkContext context) {
        return new DisplayTargetStats(4, 15, this);
    }

    @Override
    public boolean requiresComponentSanitization() {
        return true;
    }

}
