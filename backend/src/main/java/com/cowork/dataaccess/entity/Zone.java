package com.cowork.dataaccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/** Khu vực / tầng trong chi nhánh. Bảng: {@code zone}. */
@Entity
@Table(name = "zone")
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int floor = 1;

    @Column(name = "floor_plan_url", length = 500)
    private String floorPlanUrl;

    @OneToMany(mappedBy = "zone")
    private List<SpaceResource> resources = new ArrayList<>();

    public Zone() {
    }

    public Zone(Branch branch, String name, int floor, String floorPlanUrl) {
        this.branch = branch;
        this.name = name;
        this.floor = floor;
        this.floorPlanUrl = floorPlanUrl;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Branch getBranch() {
        return branch;
    }

    public void setBranch(Branch branch) {
        this.branch = branch;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getFloor() {
        return floor;
    }

    public void setFloor(int floor) {
        this.floor = floor;
    }

    public String getFloorPlanUrl() {
        return floorPlanUrl;
    }

    public void setFloorPlanUrl(String floorPlanUrl) {
        this.floorPlanUrl = floorPlanUrl;
    }

    public List<SpaceResource> getResources() {
        return resources;
    }
}
