package org.inventivetalent.bookshelves;

import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;

import java.util.List;

public class MetaHelper {

	public static <T> T setMetaValue(Metadatable meta, String key, T value) {
		meta.setMetadata(key, new FixedMetadataValue(Bookshelves.instance, value));
		return value;
	}

	@SuppressWarnings("unchecked")
	public static <T> T getMetaValue(Metadatable metadatable, String key, Class<T> clazz) {
		List<MetadataValue> meta = metadatable.getMetadata(key);
		for (MetadataValue m : meta) {
			if (clazz.isInstance(m.value()) || m.value().getClass().isAssignableFrom(clazz)) {
				return (T) m.value();
			}
		}
		return null;
	}

}
