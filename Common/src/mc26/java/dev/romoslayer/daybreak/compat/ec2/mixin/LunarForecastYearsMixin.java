package dev.romoslayer.daybreak.compat.ec2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.corgitaco.enhancedcelestials2core.api.lunarevent.LunarDimensionSettings;
import dev.corgitaco.enhancedcelestials2core.api.lunarevent.LunarForecast;
import dev.romoslayer.daybreak.compat.ec2.Ec2Years;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * EC2 keeps the lunar events of last year, this year and next year on the world clock, and drops every other year. Far
 * enough east or west a place's local year is a different one, which would have no events at all. Ec2Compat asks for
 * the years around each player's own local year, and they are rolled and kept here the same way EC2 handles its own
 * (rolled from the seed, so a year comes out the same whenever and wherever it is rolled) and synced to the players.
 */
@Mixin(LunarForecast.class)
abstract class LunarForecastYearsMixin implements Ec2Years {
	@Shadow
	@Final
	private Long2ObjectOpenHashMap<List<?>> lunarEventsByYear;
	@Shadow
	@Final
	private Holder<LunarDimensionSettings> lunarDimensionSettings;
	@Unique
	private final LongOpenHashSet daybreak$kept = new LongOpenHashSet();

	@Shadow
	private List<?> computeYearEvents(long year, LunarDimensionSettings settings) {
		throw new AssertionError();
	}

	@Shadow
	private void saveAndSync() {
		throw new AssertionError();
	}

	@Override
	public void daybreak$keepYears(LongSet years) {
		if (!this.daybreak$kept.equals(years)) {
			this.daybreak$kept.clear();
			this.daybreak$kept.addAll(years);
		}
		LunarDimensionSettings settings = this.lunarDimensionSettings.value();
		boolean added = false;
		for (LongIterator it = years.iterator(); it.hasNext();) {
			long year = it.nextLong();
			if (year >= 0 && !this.lunarEventsByYear.containsKey(year)) {
				this.lunarEventsByYear.put(year, this.computeYearEvents(year, settings));
				added = true;
			}
		}
		if (added) {
			this.saveAndSync();
		}
	}

	@Override
	public long daybreak$yearTicks() {
		LunarDimensionSettings settings = this.lunarDimensionSettings.value();
		return settings.dayLength() * settings.yearLengthInDays();
	}

	/** EC2 drops the years it does not need itself when its day changes: keep the players' years. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	@WrapOperation(method = "buildSchedule",
			at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap$FastEntrySet;removeIf(Ljava/util/function/Predicate;)Z"))
	private boolean daybreak$keepPlayersYears(Long2ObjectMap.FastEntrySet entries, Predicate drop, Operation<Boolean> original) {
		if (this.daybreak$kept.isEmpty()) {
			return original.call(entries, drop);
		}
		Predicate<Long2ObjectMap.Entry<?>> unneeded = drop;
		Predicate<Long2ObjectMap.Entry<?>> keepPlayers = entry -> unneeded.test(entry) && !this.daybreak$kept.contains(entry.getLongKey());
		return original.call(entries, keepPlayers);
	}
}
