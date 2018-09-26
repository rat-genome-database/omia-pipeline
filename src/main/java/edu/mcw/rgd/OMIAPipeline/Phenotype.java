package edu.mcw.rgd.OMIAPipeline;

/**
 * Created by cdursun on 4/21/2017.
 */
public class Phenotype implements Comparable <Phenotype> {

    public Phenotype(Integer id){
        this.id = id;
    }
    public Phenotype(Integer id, String name){
        this.id = id;
        this.name = name;
    }
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    Integer id;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    String name;

    @Override
    public boolean equals(Object obj) {
        return this.id == ((Phenotype)obj).getId();
    }

    @Override
    public int compareTo(Phenotype phene) {
        return (this.id < phene.id ) ? -1: (this.id > phene.id) ? 1:0;
    }
}
