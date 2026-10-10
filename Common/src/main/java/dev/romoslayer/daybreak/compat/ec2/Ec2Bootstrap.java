package dev.romoslayer.daybreak.compat.ec2;

/**
 * Starts the Enhanced Celestials module when EC2 is installed. Kept free of EC2 classes, so it is safe to load on a
 * server without EC2 (Ec2Compat itself refers to EC2's classes).
 */
public final class Ec2Bootstrap {
	static final String MARKER_CLASS = "dev/corgitaco/enhancedcelestials2core/EnhancedCelestials.class";

	private Ec2Bootstrap() {
	}

	static boolean installed() {
		return Ec2Bootstrap.class.getClassLoader().getResource(MARKER_CLASS) != null;
	}

	public static void init() {
		if (installed()) {
			Ec2Compat.init();
		}
	}
}
