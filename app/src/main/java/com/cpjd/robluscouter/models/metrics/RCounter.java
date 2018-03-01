package com.cpjd.robluscouter.models.metrics;

import com.cpjd.robluscouter.utils.Utils;

import org.codehaus.jackson.annotate.JsonTypeName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Represents a single, numerical value.
 * @see com.cpjd.robluscouter.models.metrics.RMetric for more information
 *
 * RCounter no longer contains a min or max value. Use Slider for this.
 * However, it does allow decimal values. Roblu will show up to 2 decimal places
 * and truncate any trailing 0s.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("RCounter")
public class RCounter extends RMetric {

    /**
     * Changing this versionUID will render this class incompatible with older versions.
     */
    public static final long serialVersionUID = 1L;
    /**
     * Represents the current value in integer form
     */
    private double value;

    /**
     * The amount to add to the value each PLUS or MINUS action.
     * Must be positive.
     */
    private double increment;

    /**
     * If true, this class will just load a textfield that is numerical only
     */
    private boolean verboseInput;

    /**
     * The empty constructor is required for de-serialization
     */
    @SuppressWarnings("unused")
    public RCounter() {}

    /**
     * Instantiates an RCounter object
     * @param ID the unique identifier for this object
     * @param title object title
     * @param increment an integer value representing the amount to add or remove to value each time
     * @param value the current integer or double value
     */
    private RCounter(int ID, String title, double increment, double value) {
        super(ID, title);
        this.value = value;
        this.increment = increment;
    }

    /**
     * Add the specified increment to the current value
     */
    public void add() {
        value += increment;
    }

    /**
     * Subtract the specified increment from the current value
     */
    public void minus() {
        value -= increment;
    }

    /**
     * If the RCounter contains a double value, then return a 2 decimal place value, otherwise, return a 0 decimal place value
     * @return the formatted value of this string
     */
    public String getTextValue() {
        if((int)value == value && (int)increment == increment) return String.valueOf((int)value);
        else return String.valueOf(Utils.round(value, 2));
    }

    @Override
    public String getFormDescriptor() {
        return "Type: Counter\nDefault value: "+value+"\nIncrement: "+increment;
    }

    @Override
    public RMetric clone() {
        RCounter counter = new RCounter(ID, title, increment, value);
        counter.setVerboseInput(verboseInput);
        counter.setRequired(required);
        return counter;
    }
    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
