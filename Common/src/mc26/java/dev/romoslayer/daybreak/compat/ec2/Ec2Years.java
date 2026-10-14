package dev.romoslayer.daybreak.compat.ec2;

import it.unimi.dsi.fastutil.longs.LongSet;

/** Added to EC2's forecast (LunarForecastYearsMixin): which years of lunar events it keeps besides its own. */
public interface Ec2Years {
	/** Keep these years (and roll any that are missing, the way EC2 rolls its own); years before 0 are ignored. */
	void daybreak$keepYears(LongSet years);

	/** The length of EC2's year in clock ticks. */
	long daybreak$yearTicks();
}
