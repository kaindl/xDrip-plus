package com.eveningoutpost.dexdrip.UtilityModels;

import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.Models.Treatments;
import com.eveningoutpost.dexdrip.Models.UserError;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jamorham on 16/06/2016.
 */
public class UndoRedo {

    private static int EXPIRY_TIME = (1000 * 60 * 30);
    private static String TAG = "UndoRedo";

    private static final List<Undo_item> undo_queue = new ArrayList<>();
    private static final List<Undo_item> redo_queue = new ArrayList<>();
    private static final Object queue_lock = new Object();

    public static boolean undoListHasItems()
    {
        synchronized (queue_lock) {
            return (undo_queue.size() > 0);
        }
    }
    public static boolean redoListHasItems()
    {
        synchronized (queue_lock) {
            return (redo_queue.size() > 0);
        }
    }

    public static void addUndoTreatment(String treatment_uuid)
    {
        if (treatment_uuid == null) return;
        synchronized (queue_lock) {
            undo_queue.add(new Undo_item(treatment_uuid,null));
            redo_queue.clear();
        }
    }

    public static void addUndoCalibration(String calibration_uuid)
    {
        if (calibration_uuid == null) return;
        synchronized (queue_lock) {
            undo_queue.add(new Undo_item(null,calibration_uuid));
            redo_queue.clear();
        }
    }

    public static void purgeQueues() {
        final double now = JoH.ts();
        boolean queueChanged = false;
        synchronized (queue_lock) {
            for (Undo_item item : undo_queue) {
                if (item.expires < now) {
                    undo_queue.remove(item);
                    queueChanged = true;
                    break;
                }
            }
            for (Undo_item item : redo_queue) {
                if (item.expires < now) {
                    redo_queue.remove(item);
                    queueChanged = true;
                    break;
                }
            }
        }
        if (queueChanged) purgeQueues();
    }

    public static boolean undoNextItem() {
        if (undoListHasItems()) {
            synchronized (queue_lock) {
                final int location = undo_queue.size() - 1;
                Undo_item item = undo_queue.get(location);
                if (item.Treatment_uuid != null) {
                    try {
                        item.saved_data = Treatments.byuuid(item.Treatment_uuid).toJSON();
                        item.expires = JoH.ts() + EXPIRY_TIME;
                        redo_queue.add(item);
                        undo_queue.remove(location);
                        Treatments.delete_by_uuid(item.Treatment_uuid,true);
                    } catch (NullPointerException e) {
                        UserError.Log.wtf(TAG, "Null pointer exception in undoNext()");
                        undo_queue.remove(location);
                    }
                    return true;
                } else if (item.Calibration_uuid != null) {
                    // TODO try catch
                    item.saved_data = Calibration.byuuid(item.Calibration_uuid).toS();
                    item.expires = JoH.ts() + EXPIRY_TIME;
                    redo_queue.add(item);
                    undo_queue.remove(location);
                    Calibration.clear_byuuid(item.Calibration_uuid, true); // from interactive
                    return true;
                }

            }
            return false;
        } else {
            return false;
        }

    }

    public static boolean redoNextItem()
    {
        if (redoListHasItems())
        {
            synchronized (queue_lock) {
                final int location = redo_queue.size() - 1;
                Undo_item item = redo_queue.get(location);
                // check saved data innit

                if (item.Treatment_uuid !=null)
                {
                    // TODO try catch
                    Treatments.pushTreatmentFromJson(item.saved_data,true); // from interactive
                    undo_queue.add(item);
                    redo_queue.remove(location);
                    return true;
                }

                // TODO do same for calibrations
            }
            return false;
        }  else { return false; }

    }

    private static class Undo_item {
        public double expires;
        public String Treatment_uuid;
        public String Calibration_uuid;
        public String saved_data;


        public Undo_item(String treatment_uuid, String calibration_uuid) {
            expires = JoH.ts()+EXPIRY_TIME;
            Calibration_uuid = calibration_uuid;
            Treatment_uuid = treatment_uuid;
        }
    }

}