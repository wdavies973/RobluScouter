package com.cpjd.robluscouter.models;

import com.cpjd.robluscouter.models.metrics.RMetric;

import java.io.Serializable;
import java.util.ArrayList;

import lombok.Data;

/**
 * Stores form data for the PIT and Match forms.
 *
 * @version 2
 * @since 3.0.0
 * @author Will Davies
 */
@Data
public class RForm implements Serializable {

    /**
     * Changing this versionUID will render this class incompatible with older versions.
     */
    public static final long serialVersionUID = 1L;
    /**
     * Represents the pit metric form. Pit form will be used to generate a PIT form for every team.
     * It's mandatory
     */
    private ArrayList<RMetric> pit;
    /**
     * Represents the match metric form. Multiple match forms can be used for each team.
     * It also it used to generate a predictions tab.
     */
    private ArrayList<RMetric> match;

    /**
     * The empty constructor is required for de-serialization
     */
    @SuppressWarnings("unused")
    public RForm() {}

    /**
     * Creates a form model
     * @param pit ArrayList of pit metrics
     * @param match ArrayList of match metrics
     */
    public RForm(ArrayList<RMetric> pit, ArrayList<RMetric> match) {
        this.pit = pit;
        this.match = match;
    }
}
