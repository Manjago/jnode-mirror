package jnode.dto;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Планировщик (жалкая замена cron :-) )
 *
 * @author Manjago
 */
@DatabaseTable(tableName = "schedule")
public class Schedule {
    private static final DateFormat dateFormat = new SimpleDateFormat("MMMM dd yyyy");
    @DatabaseField(columnName = "id", generatedId = true)
    private Long id;
    @DatabaseField(dataType = DataType.ENUM_STRING, canBeNull = false, columnName = "type", defaultValue = "DAILY")
    private Type type;
    @DatabaseField(columnName = "details", defaultValue = "0")
    private Integer details;
    @DatabaseField(columnName = "jscript_id", foreign = true, canBeNull = false, uniqueIndexName = "lsched_idx")
    private Jscript jscript;
    @DatabaseField(columnName = "lastRunDate", canBeNull = true, dataType = DataType.DATE)
    private Date lastRunDate;

    private static boolean isSameDay(Date date1, Date date2) {
        return !(date1 == null || date2 == null) && dateFormat.format(date1).equals(dateFormat.format(date2));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getLastRunDate() {
        return lastRunDate;
    }

    public void setLastRunDate(Date lastRunDate) {
        this.lastRunDate = lastRunDate;
    }

    public boolean isNeedExec(Calendar calendar) {

        if (calendar == null || getType() == null || getDetails() == null) {
            return false;
        }

        if (isSameDay(getLastRunDate(), new Date())) {
            return false;
        }

        switch (getType()) {
            case DAILY:
                return true;
            case ANNUALLY:
                return checkDetails(calendar.get(Calendar.DAY_OF_YEAR));
            case MONTHLY:
                return checkDetails(calendar.get(Calendar.DAY_OF_MONTH));
            case WEEKLY:
                return checkDetails(calendar.get(Calendar.DAY_OF_WEEK));

            default:
                return false;
        }
    }

    private boolean checkDetails(int fromCalendar) {
        return getDetails() != null && getDetails().equals(fromCalendar);
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Integer getDetails() {
        return details;
    }

    public void setDetails(Integer details) {
        this.details = details;
    }

    public Jscript getJscript() {
        return jscript;
    }

    public void setJscript(Jscript jscript) {
        this.jscript = jscript;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Schedule{");
        sb.append("id=").append(id);
        sb.append(", type=").append(type);
        sb.append(", details=").append(details);
        sb.append(", jscript=").append(jscript);
        sb.append(", lastRunDate=").append(lastRunDate);
        sb.append('}');
        return sb.toString();
    }

    public static enum Type {
        DAILY, WEEKLY, MONTHLY, ANNUALLY
    }
}
