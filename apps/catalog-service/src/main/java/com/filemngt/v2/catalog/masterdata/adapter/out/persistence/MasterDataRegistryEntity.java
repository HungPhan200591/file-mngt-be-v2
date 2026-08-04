package com.filemngt.v2.catalog.masterdata.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "master_data_registry")
public class MasterDataRegistryEntity {

    @Id
    private int id;

    @Column(nullable = false)
    private long version;

    protected MasterDataRegistryEntity() {}

    public int id() {
        return id;
    }

    public long version() {
        return version;
    }

    public void incrementVersion() {
        this.version++;
    }
}
