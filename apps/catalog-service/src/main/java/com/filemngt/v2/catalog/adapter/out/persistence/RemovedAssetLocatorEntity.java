package com.filemngt.v2.catalog.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "catalog_removed_asset_locator")
@IdClass(RemovedAssetLocatorEntity.Key.class)
public class RemovedAssetLocatorEntity {
    @Id
    private String storageKey;

    @Id
    private String relativePath;

    private Instant removedAt;

    protected RemovedAssetLocatorEntity() {}

    public RemovedAssetLocatorEntity(String storageKey, String relativePath, Instant removedAt) {
        this.storageKey = storageKey;
        this.relativePath = relativePath;
        this.removedAt = removedAt;
    }

    public Instant removedAt() {
        return removedAt;
    }

    public static final class Key implements Serializable {
        private String storageKey;
        private String relativePath;

        public Key() {}

        public Key(String storageKey, String relativePath) {
            this.storageKey = storageKey;
            this.relativePath = relativePath;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Key other)) return false;
            return Objects.equals(storageKey, other.storageKey) && Objects.equals(relativePath, other.relativePath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(storageKey, relativePath);
        }
    }
}
