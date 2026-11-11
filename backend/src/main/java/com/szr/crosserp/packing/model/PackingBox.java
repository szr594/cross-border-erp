package com.szr.crosserp.packing.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="packing_boxes")
public class PackingBox {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="box_no",nullable=false,unique=true,length=64) private String boxNo;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="packing_task_id",nullable=false) private PackingTask task;
    @Column(name="weight_grams") private Integer weightGrams;
    @Column(name="length_mm") private Integer lengthMm;
    @Column(name="width_mm") private Integer widthMm;
    @Column(name="height_mm") private Integer heightMm;
    @Column(length=500) private String note;
    @Version @Column(nullable=false) private long version;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected PackingBox() {}
    public PackingBox(String boxNo,PackingTask task,Integer weight,Integer length,Integer width,Integer height,String note,Instant now){this.boxNo=boxNo;this.task=task;update(weight,length,width,height,note,now);this.createdAt=now;}
    public void update(Integer weight,Integer length,Integer width,Integer height,String note,Instant now){validate(weight,length,width,height);this.weightGrams=weight;this.lengthMm=length;this.widthMm=width;this.heightMm=height;this.note=note;this.updatedAt=now;}
    private void validate(Integer weight,Integer length,Integer width,Integer height){if(weight!=null&&weight<=0)throw new IllegalArgumentException();boolean none=length==null&&width==null&&height==null;boolean all=length!=null&&width!=null&&height!=null&&length>0&&width>0&&height>0;if(!none&&!all)throw new IllegalArgumentException();}
    public Long getId(){return id;} public String getBoxNo(){return boxNo;} public PackingTask getTask(){return task;} public Integer getWeightGrams(){return weightGrams;} public Integer getLengthMm(){return lengthMm;} public Integer getWidthMm(){return widthMm;} public Integer getHeightMm(){return heightMm;} public String getNote(){return note;} public long getVersion(){return version;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
