package dev.romoslayer.daybreak.compat.ec2;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** The Enhanced Celestials mixins only load when Enhanced Celestials 2: Core is installed (works on both loaders). */
public final class Ec2MixinPlugin implements IMixinConfigPlugin {
	private boolean installed;

	@Override
	public void onLoad(String mixinPackage) {
		this.installed = Ec2MixinPlugin.class.getClassLoader().getResource(Ec2Bootstrap.MARKER_CLASS) != null;
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return this.installed;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	/**
	 * Only called once a mixin has been applied in full, so this is the reliable sign that its hooks are in place. Shared
	 * through a system property because the plugin and the mod may not share a class loader.
	 */
	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		System.setProperty(APPLIED_PREFIX + mixinClassName, "true");
	}

	static final String APPLIED_PREFIX = "daybreak.mixinApplied.";
}
