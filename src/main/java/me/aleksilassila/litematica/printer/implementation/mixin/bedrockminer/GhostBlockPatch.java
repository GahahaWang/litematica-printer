package me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.github.bunnyi116.bedrockminer.task.Task", remap = false)
public abstract class GhostBlockPatch {

	@Shadow
	@Final
	public BlockPos pos;

	@Shadow
	public abstract boolean isComplete();

	@Inject(method = "tick", at = @At("HEAD"))
	private void litematica_printer$afterTick(CallbackInfo ci) {
		if (isComplete()) {
			litematica_printer$requestServerStateSync();
		}
	}

	@Unique
	private boolean litematica_printer$requestServerStateSync() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.gameMode == null || client.getConnection() == null) {
			return false;
		}
		if (!com.github.bunnyi116.bedrockminer.util.PlayerUtils.canInteractWithBlockAt(this.pos, 0)) {
			return false;
		}

		Inventory inventory = player.getInventory();
		int originalSlot = inventory.getSelectedSlot();
        com.github.bunnyi116.bedrockminer.util.InventoryUtils.autoSwitch(Blocks.PISTON);
		Direction face = com.github.bunnyi116.bedrockminer.util.PlayerUtils.getClosestFace(this.pos);
		BlockHitResult hitResult = litematica_printer$makeHitResult(this.pos, face);
		client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
        inventory.setSelectedSlot(originalSlot);
        client.getConnection().send(new ServerboundSetCarriedItemPacket(originalSlot));

		return true;
	}

	@Unique
	private static BlockHitResult litematica_printer$makeHitResult(BlockPos blockPos, Direction facing) {
		BlockPos hitPos = blockPos.relative(facing.getOpposite());
		Vec3 hitVec3d = Vec3.atCenterOf(hitPos).relative(facing, 0.5F);
		return new BlockHitResult(hitVec3d, facing, blockPos, false);
	}
}
