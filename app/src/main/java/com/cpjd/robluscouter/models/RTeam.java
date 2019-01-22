package com.cpjd.robluscouter.models;

import com.cpjd.robluscouter.models.metrics.RBoolean;
import com.cpjd.robluscouter.models.metrics.RCalculation;
import com.cpjd.robluscouter.models.metrics.RCheckbox;
import com.cpjd.robluscouter.models.metrics.RChooser;
import com.cpjd.robluscouter.models.metrics.RCounter;
import com.cpjd.robluscouter.models.metrics.RFieldDiagram;
import com.cpjd.robluscouter.models.metrics.RMetric;
import com.cpjd.robluscouter.models.metrics.RSlider;
import com.cpjd.robluscouter.models.metrics.RStopwatch;
import com.cpjd.robluscouter.models.metrics.RTextfield;
import com.cpjd.robluscouter.utils.Utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;

import lombok.Data;

/**
 * Welcome to the belly of the beast! (Not really)
 * This class models what data a team should store.
 *
 * @version 2
 * @since 3.0.0
 * @author Will Davies
 */
@Data
@SuppressWarnings("unused")
public class RTeam implements Serializable {

    /**
     * Changing this versionUID will render this class incompatible with older versions.
     */
    public static final long serialVersionUID = 1L;

    /**
     * Unique identifier for this team. No duplicate IDs allowed. Completely arbitrary.
     */
    private int ID;

    /**
     * Name of this team, can be really anything. Usually will be pulled from the Blue Alliance.
     */
    private String name;

    /**
     * Number of this team
     */
    private int number;

    /**
     * Time stamp of last edit made to this team. Also used for resolving merge conflicts
     */
    private long lastEdit;

    /**
     * Stores the scouting data. See RTab for more info.
     * tabs.get(0) is always the PIT tab
     * tabs.get(1) is always the Predictions tab
     */
    private ArrayList<RTab> tabs;

    /**
     * tabs.get(page) is the page that the user looked at last
     */
    private int page;

    /**
     * In order to make the user base happier by downloading less data,
     * TBA data is only downloaded once. Roblu can infer whether the download
     * happened by checking the below data.
     *
     * These aren't used by RobluScouter
     */
    private String fullName, location, motto, website;
    private int rookieYear;

    /**
     * The empty constructor is required for de-serialization
     */
    @SuppressWarnings("unused")
    public RTeam() {}

    /**
     * Creates a new RTeam with default values
     * @param name the team's name
     * @param number the team's number
     * @param ID the arbitrary, unique identifier for this team
     */
    public RTeam(String name, int number, int ID) {
        this.name = name;
        this.number = number;
        this.ID = ID;
        this.page = 1; // set default page to PIT

        lastEdit = 0;
    }

    /**
     * verify() makes sure that the form and team are synchronized. Here's what it does:
     * <p>
     * PIT:
     * -If the user modified the form and ADDED elements, then we'll make sure to add them to this team's form copy
     * -If the user modified the form and REMOVED elements, then we'll make sure to remove them from this team's form copy
     * -If the user changed any item titles, change them right away
     * -If the user changed any default values, reset all the values on all elements that have NOT been modified
     * -If the user changed the order of any elements, change the order
     * <p>
     * MATCH:
     * -If the user modified the match form and ADDED elements, then we'll add those to EVERY match profile
     * -If the user modified the match form and REMOVED elements, then we'll remove those from EVERY match profile
     * -If the user changed any item titles, change them on ALL match profiles
     * -If the user changed any default values, reset all the values of EVERY match that have NOT been modified
     * -If the user changed the order of any elements, change the order
     * <p>
     * PREMISE:
     * -PIT and MATCH form arrays may NOT be null, only empty
     * <p>
     * NULLS to check for:
     * -If the team has never been opened before, set the PIT values, matches don't need to be set until creation.
     */
    public void verify(RForm form) {
        // Check for null or missing Pit & Predictions tabs
        if(this.tabs == null || this.tabs.size() == 0) {
            this.tabs = new ArrayList<>();
            addTab(new RTab(number, "Pit", Utils.duplicateRMetricArray(form.getPit()) , false, false, 0));
            addTab(new RTab(number, "Predictions", Utils.duplicateRMetricArray(form.getMatch()) , false, false, 0));
            // Check to make sure the team name and number have been inserted into the form
            for(RMetric m : this.getTabs().get(0).getMetrics()) {
                if(m.getID() == 0) ((RTextfield)m).setText(name); // team name
                else if(m.getID() == 1) ((RTextfield)m).setText(String.valueOf(number)); // team number
            }

            return;
        }

        // Remove elements that aren't on the form
        ArrayList<RMetric> temp = form.getPit(); // less if statements, just switches between PIT or MATCH depending on what needs to be verified
        for(int i = 0; i < tabs.size(); i++) {
            if(!tabs.get(i).getTitle().equalsIgnoreCase("Pit")) temp = form.getMatch();
            for (int j = 0; j < tabs.get(i).getMetrics().size(); j++) {
                boolean found = false;
                if(temp.size() == 0) {
                    tabs.get(i).getMetrics().clear();
                    break;
                }
                for (int k = 0; k < temp.size(); k++) {
                    if (tabs.get(i).getMetrics().get(j).getID() == temp.get(k).getID()) found = true;
                    if (k == temp.size() - 1 && !found) {
                        tabs.get(i).getMetrics().remove(j);
                        j = 0;
                        break;
                    }
                }
            }
        }

        /*
         * Alright, so we removed old elements, but we're still very suspicious. For example,
         * let's say the user edited the form (by removing an element), didn't re-verify any teams (by not opening them),
         * and then added a new element in the position of the old one. What will happen is that the above method will
         * NOT remove the old metric instance, and instead below the metrics name will not get updated. So, here we will
         * make sure that both the ID and INSTANCE TYPE of each metric (in the form and team) match, otherwise, the metric
         * gets booted.
         */
        temp = form.getPit();
        for(int i = 0; i < tabs.size(); i++) {
            if(!tabs.get(i).getTitle().equalsIgnoreCase("Pit")) temp = form.getMatch();
            for(int j = 0; j < temp.size(); j++) {
                for(int k = 0; k < tabs.get(i).getMetrics().size(); k++) {
                    if(tabs.get(i).getMetrics().get(k).getID() == temp.get(j).getID()) {
                        if(!temp.get(j).getClass().equals(tabs.get(i).getMetrics().get(k).getClass())) {
                            tabs.get(i).getMetrics().remove(k);
                            j = 0;
                            k = 0;
                        }
                    }
                }
            }
        }

        // Add elements that are on the form, but not in this team
        temp = form.getPit();
        for(int i = 0; i < tabs.size(); i++) {
            if(!tabs.get(i).getTitle().equalsIgnoreCase("Pit")) temp = form.getMatch();
            for (int j = 0; j < temp.size(); j++) {
                boolean found = false;
                if(tabs.get(i).getMetrics().size() == 0) {
                    tabs.get(i).getMetrics().add(temp.get(j).clone());
                    continue;
                }
                for (int k = 0; k < tabs.get(i).getMetrics().size(); k++) {
                    if (tabs.get(i).getMetrics().get(k).getID() == temp.get(j).getID()) found = true;
                    if (k == tabs.get(i).getMetrics().size() - 1 && !found) {
                        tabs.get(i).getMetrics().add(temp.get(j).clone());
                        j = 0;
                        break;
                    }
                }
            }
        }

        // Update item names
        temp = form.getPit();
        for(int i = 0; i < tabs.size(); i++) {
            if(!tabs.get(i).getTitle().equalsIgnoreCase("PIT")) temp = form.getMatch();
            for (int j = 0; j < temp.size(); j++) {
                for (int k = 0; k < tabs.get(i).getMetrics().size(); k++) {
                    if (temp.get(j).getID() == tabs.get(i).getMetrics().get(k).getID()) {
                        tabs.get(i).getMetrics().get(k).setTitle(temp.get(j).getTitle());
                        break;
                    }
                }
            }
        }

        // Update default values for non-modified values, also check for some weird scenarios
        temp = form.getPit();
        for(int i = 0; i < tabs.size(); i++) {
            if(!tabs.get(i).getTitle().equalsIgnoreCase("PIT")) temp = form.getMatch();
            for(int j = 0; j < temp.size(); j++) {
                for(int k = 0; k < tabs.get(i).getMetrics().size(); k++) {
                    if (temp.get(j).getID() == tabs.get(i).getMetrics().get(k).getID()) {
                        RMetric e = temp.get(j);
                        RMetric s = tabs.get(i).getMetrics().get(k);

                        if(e instanceof RBoolean && !s.isModified() && s instanceof RBoolean)
                            ((RBoolean) s).setValue(((RBoolean) e).isValue());
                        else if(e instanceof RFieldDiagram && s instanceof RFieldDiagram && (((RFieldDiagram) e).getPictureID() != ((RFieldDiagram) s).getPictureID())) {
                            // Remove old picture drawings
                            ((RFieldDiagram) s).setDrawings(null);
                            ((RFieldDiagram) s).setPictureID(((RFieldDiagram) e).getPictureID());
                        }
                        else if(e instanceof RCounter && !s.isModified() && s instanceof RCounter) {
                            ((RCounter)s).setValue(((RCounter)e).getValue());
                        }
                        else if(e instanceof RCalculation && s instanceof RCalculation) {
                            ((RCalculation) s).setCalculation(((RCalculation) e).getCalculation());
                        }
                        else if(e instanceof RCheckbox && s instanceof RCheckbox) {
                            // update the titles always, rely on position for this one
                            LinkedHashMap<String, Boolean> newHash = new LinkedHashMap<>();
                            // get old values
                            ArrayList<Boolean> values = new ArrayList<>();
                            // put values from team checkbox into this array
                            for(String oKey : ((RCheckbox) s).getValues().keySet()) {
                                values.add(((RCheckbox) s).getValues().get(oKey));
                            }
                            // copy values to new linked hash map, replacing the new keys
                            int index = 0;
                            for(String oKey : ((RCheckbox) e).getValues().keySet()) {
                                newHash.put(oKey, values.get(index));
                                index++;
                            }
                            // set new array back to team metric
                            ((RCheckbox) s).setValues(newHash);

                            // if the checkbox is not modified, reset the boolean values
                            if(!s.isModified()) {
                                for(String key : ((RCheckbox)e).getValues().keySet()) ((RCheckbox)s).getValues().put(key, ((RCheckbox) e).getValues().get(key));
                            }
                        }
                        // if one line is true, it means its the team name or number metric and its value shouldn't be overrided
                        else if(e instanceof RTextfield && !s.isModified() && !((RTextfield) e).isOneLine()) ((RTextfield) s).setText(((RTextfield) e).getText());
                        else if(e instanceof RChooser && s instanceof RChooser) {
                            // Always update the title
                            if (!Arrays.equals(((RChooser) s).getValues(), ((RChooser) e).getValues())) {
                                ((RChooser) s).setValues(((RChooser) e).getValues());
                            }
                            // if the chooser is not modified, reset the chooser values
                            if(!s.isModified())
                                ((RChooser) s).setSelectedIndex(((RChooser) e).getSelectedIndex());
                        } else if (e instanceof RStopwatch && !s.isModified() && s instanceof RStopwatch)
                            ((RStopwatch) s).setTime(((RStopwatch) e).getTime());
                        else if (e instanceof RSlider && !s.isModified() && s instanceof RSlider) {
                            ((RSlider) s).setMax(((RSlider) e).getMax());
                            ((RSlider) s).setMin(((RSlider) e).getMin());
                            ((RSlider) s).setValue(((RSlider) e).getValue());
                        } else if (e instanceof RCounter && s instanceof RCounter) {
                            ((RCounter) s).setIncrement(((RCounter) e).getIncrement());

                            ((RCounter) s).setVerboseInput(((RCounter) e).isVerboseInput());
                            if (!s.isModified())
                                ((RCounter) s).setValue(((RCounter) e).getValue());
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * Adds the tab to the team
     * @param tab the new RTab to add
     * @return index of sorted, newly added tab
     */
    int addTab(RTab tab) {
        tabs.add(tab);
        Collections.sort(tabs);
        for(int i = 0; i < tabs.size(); i++) if(tabs.get(i).getTitle().equals(tab.getTitle())) return i;
        return 1;
    }
    /**
     * Deletes the tab from the RTabs array
     * @param position the index or position of the tab to delete
     */
    public void removeTab(int position) {
        tabs.remove(position);
    }

    /**
     * Shortcut method to get the elements from the specified tab
     * @param page the tab index
     * @return the form elements from that index
     */
    public ArrayList<RMetric> getMetrics(int page) {
        return tabs.get(page).getMetrics();
    }
    /**
     * Removes all the tab except the PIT and PREDICTIONS tabs
     */
    public void removeAllTabsButPIT() {
        if(tabs == null || tabs.size() == 0) return;

        RTab pit = tabs.get(0);
        RTab predictions = tabs.get(1);
        tabs.clear();
        tabs.add(pit);
        tabs.add(predictions);
    }
    /**
     * Returns the number of matches this team is in
     * @return returns the number of matches this team contains
     */
    public int getNumMatches() {
        if(tabs == null) return 0;
        else return tabs.size() - 2;
    }
}
