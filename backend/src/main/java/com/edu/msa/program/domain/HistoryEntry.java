package com.edu.msa.program.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class HistoryEntry {
    private String ver;
    private String date;
    @Column(columnDefinition = "text")
    private String log;

    protected HistoryEntry() {}

    public HistoryEntry(String ver, String date, String log) {
        this.ver = ver;
        this.date = date;
        this.log = log;
    }

    public String getVer() { return ver; }
    public String getDate() { return date; }
    public String getLog() { return log; }
}
