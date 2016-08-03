package org.iclick.pra.cinema_theater;

import java.io.Serializable;

/**
 * Represents a bean class for movies
 */
public class Movie implements Serializable {

    private String RESULT;
    private String POSTER_PATH;
    private String OVERVIEW;
    private String RELEASE_DATE;
    private String ID;
    private String TITLE;
    private String LANGUAGE;
    private String BACKDROP_PATH;
    private String POPULARITY;

    public String getRESULT() {
        return RESULT;
    }

    public void setRESULT(String RESULT) {
        this.RESULT = RESULT;
    }

    public String getPOSTER_PATH() {
        return POSTER_PATH;
    }

    public void setPOSTER_PATH(String POSTER_PATH) {
        this.POSTER_PATH = POSTER_PATH;
    }

    public String getOVERVIEW() {
        return OVERVIEW;
    }

    public void setOVERVIEW(String OVERVIEW) {
        this.OVERVIEW = OVERVIEW;
    }

    public String getRELEASE_DATE() {
        return RELEASE_DATE;
    }

    public void setRELEASE_DATE(String RELEASE_DATE) {
        this.RELEASE_DATE = RELEASE_DATE;
    }

    public String getID() {
        return ID;
    }

    public void setID(String ID) {
        this.ID = ID;
    }

    public String getTITLE() {
        return TITLE;
    }

    public void setTITLE(String TITLE) {
        this.TITLE = TITLE;
    }

    public String getLANGUAGE() {
        return LANGUAGE;
    }

    public void setLANGUAGE(String LANGUAGE) {
        this.LANGUAGE = LANGUAGE;
    }

    public String getBACKDROP_PATH() {
        return BACKDROP_PATH;
    }

    public void setBACKDROP_PATH(String BACKDROP_PATH) {
        this.BACKDROP_PATH = BACKDROP_PATH;
    }

    public String getPOPULARITY() {
        return POPULARITY;
    }

    public void setPOPULARITY(String POPULARITY) {
        this.POPULARITY = POPULARITY;
    }

    public Movie() {
    }


}
