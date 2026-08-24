package com.edu.msa.program.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProgramFile {
    @Column(name = "file_name")
    private String name;
    @Column(name = "file_size")
    private String size;
    @Column(name = "file_type")
    private String type;

    protected ProgramFile() {}

    public ProgramFile(String name, String size, String type) {
        this.name = name;
        this.size = size;
        this.type = type;
    }

    public String getName() { return name; }
    public String getSize() { return size; }
    public String getType() { return type; }
}
