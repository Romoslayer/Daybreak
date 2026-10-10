package dev.romoslayer.daybreak.time;

/**
 * Added to AttributeTrackSampler: a track of a repeating timeline (day, moon, villager schedule) built for a regional
 * level, which can be sampled at a position's local solar time.
 */
public interface RegionalTrack {
	void daybreak$markRegional();

	boolean daybreak$isRegional();

	Object daybreak$sampleAt(Object baseValue, double x);
}
