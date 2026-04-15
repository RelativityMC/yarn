package net.fabricmc.filament.nameproposal.field.nameprovider;

import net.fabricmc.filament.nameproposal.field.FieldData;

import java.util.function.UnaryOperator;
import java.util.function.BiFunction;

/**
 * A field name provider that delegates to another provider and modifies
 * the returned name.
 */
public record ModifyingFieldNameProvider(FieldNameProvider delegate, BiFunction<String, FieldData, String> modifier) implements FieldNameProvider {
	public ModifyingFieldNameProvider(FieldNameProvider delegate, UnaryOperator<String> modifier) {
		this(delegate, (name, data) -> modifier.apply(name));
	}

	public static ModifyingFieldNameProvider prefixing(FieldNameProvider delegate, String prefix) {
		return new ModifyingFieldNameProvider(delegate, name -> prefix + name);
	}

	public static ModifyingFieldNameProvider suffixing(FieldNameProvider delegate, String suffix) {
		return new ModifyingFieldNameProvider(delegate, name -> name + suffix);
	}

	@Override
	public String getName(FieldData field) {
		String delegateName = delegate.getName(field);
		return delegateName == null ? null : modifier.apply(delegateName, field);
	}
}
