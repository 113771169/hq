/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004, 2005, 2006], Hyperic, Inc.
 * This file is part of HQ.
 * 
 * HQ is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package org.hyperic.util.units;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

import org.hyperic.util.ArrayUtil;
import org.hyperic.util.StringUtil;

/**
 * Format a value into a duration.  
 */
public class DurationFormatter 
    implements Formatter
{
    private static final int GRANULAR_YEARS  = 1;  // Time > 1 year
    private static final int GRANULAR_DAYS   = 2;  // 1 day < time < 1 year 
    private static final int GRANULAR_HOURS  = 3;  // 1 hour < time < 1 day
    private static final int GRANULAR_MINS   = 4;  // 1 min < time < 1 hour
    private static final int GRANULAR_SECS   = 5;  // 0 < time < 1 min

    private static final int MILLISEC_DIGITS = 3;  // Standard 3 digits to the
                                                   // right of the decimal point
    
    private class TimeBreakDown {
        long nYears, nDays, nHours, nMins, nSecs, nMilli;
    }

    public FormattedNumber format(UnitNumber val, Locale locale,
                                  FormatSpecifics specifics)
    {
        BigDecimal baseTime;
        String res;
        int granularity;

        baseTime    = val.getBaseValue();
        granularity = this.getGranularity(baseTime);
        return this.format(baseTime, granularity, MILLISEC_DIGITS, locale);
    }

    public FormattedNumber[] formatSame(double[] val, int unitType, int scale,
                                        Locale locale, 
                                        FormatSpecifics specifics)
    {
        FormattedNumber[] res;
        double[] secs;
        UnitNumber tmpNum;
        int maxIdx, granularity, milliDigits;
        TimeBreakDown tbd;
        boolean wholeNum;
        
        res = new FormattedNumber[val.length];
        if((maxIdx = ArrayUtil.max(val)) == -1){
            return res;
        }

        tmpNum      = new UnitNumber(val[maxIdx], unitType, scale);
        granularity = this.getGranularity(tmpNum.getBaseValue());
        
        // Determine the number scale (right of the decimal point) that is
        // needed to ensure that every formatted number is unique and a
        // linear scale
        wholeNum = true;
        secs = new double[val.length];
        for(int i = 0;i < secs.length;i++) {
            tbd = this.breakDownTime(
                      UnitsFormat.getBaseValue(val[i], unitType, scale));
            secs[i] = tbd.nSecs + ((double)tbd.nMilli / 1000);
            if(tbd.nMilli > 0) wholeNum = false;
        }
        
        milliDigits = UnitsUtil.getUniqueDigits(secs, locale);

        if(milliDigits == 0 && wholeNum == false) milliDigits = 1;
        
        // Format the numbers                
        for(int i=0; i<val.length; i++){
            tmpNum = new UnitNumber(val[i], unitType, scale);
            res[i] = this.format(tmpNum.getBaseValue(), granularity,
                                 milliDigits, locale);
        }
        
        return res;
    }

    private FormattedNumber format(BigDecimal baseTime, int granularity,
                                   int milliDigits, Locale locale)
    {
        TimeBreakDown tbd;
        String res;

        // We work in a few different time ranges depending on the
        // magnitude of the duration.  Quite hardcoded
        if(granularity == GRANULAR_YEARS){
            tbd = this.breakDownTime(baseTime);
            res = tbd.nYears + "y " + tbd.nDays + "d";
        } else if(granularity == GRANULAR_DAYS){
            long nDays;

            tbd   = this.breakDownTime(baseTime);
            nDays = tbd.nYears * 365 + tbd.nDays;
            res   = nDays + (nDays == 1 ? " day " : " days ") + 
                StringUtil.formatDuration(tbd.nHours * 60 * 60 * 1000 +
                                          tbd.nMins * 60 * 1000 +
                                          tbd.nSecs * 1000 +
                                          tbd.nMilli);
        } else if(granularity == GRANULAR_HOURS ||
                  granularity == GRANULAR_MINS ||
                  granularity == GRANULAR_SECS)
        {
            long nMillis;

            nMillis = baseTime.divide(UnitsUtil.FACT_MILLIS, 
                                      BigDecimal.ROUND_HALF_EVEN).longValue();
            res = StringUtil.formatDuration(nMillis, milliDigits,
                                            granularity == GRANULAR_SECS);

            if(granularity == GRANULAR_SECS)
                res = res + 's';                                            
        } else {
            throw new IllegalStateException("Unexpected granularity");
        }

        return new FormattedNumber(res.trim(), "");
    }

    private TimeBreakDown breakDownTime(BigDecimal val){
        TimeBreakDown r = new TimeBreakDown();

        r.nYears = val.divide(UnitsUtil.FACT_YEARS, 
                              BigDecimal.ROUND_DOWN).intValue();
        if(r.nYears > 0)
            val = val.subtract(UnitsUtil.FACT_YEARS.
                               multiply(new BigDecimal(r.nYears)));
            
        r.nDays = val.divide(UnitsUtil.FACT_DAYS, 
                             BigDecimal.ROUND_DOWN).intValue();
        if(r.nDays > 0)
            val = val.subtract(UnitsUtil.FACT_DAYS.
                               multiply(new BigDecimal(r.nDays)));

        r.nHours = val.divide(UnitsUtil.FACT_HOURS, 
                              BigDecimal.ROUND_DOWN).intValue();
        if(r.nHours > 0)
            val = val.subtract(UnitsUtil.FACT_HOURS.
                               multiply(new BigDecimal(r.nHours)));

        r.nMins = val.divide(UnitsUtil.FACT_MINS, 
                             BigDecimal.ROUND_DOWN).intValue();
        if(r.nMins > 0)
            val = val.subtract(UnitsUtil.FACT_MINS.
                               multiply(new BigDecimal(r.nMins)));

        r.nSecs = val.divide(UnitsUtil.FACT_SECS, 
                             BigDecimal.ROUND_DOWN).intValue();
        if(r.nSecs > 0)
            val = val.subtract(UnitsUtil.FACT_SECS.
                               multiply(new BigDecimal(r.nSecs)));

        r.nMilli = val.divide(UnitsUtil.FACT_MILLIS, 
                              BigDecimal.ROUND_DOWN).intValue();
        return r;
    }

    private int getGranularity(BigDecimal nanoSecs){
        TimeBreakDown tbd = this.breakDownTime(nanoSecs);
        
        if(tbd.nYears > 0)
            return GRANULAR_YEARS;
        else if(tbd.nDays > 0)
            return GRANULAR_DAYS;
        else if(tbd.nHours > 0)
            return GRANULAR_HOURS;
        else if(tbd.nMins > 0)
            return GRANULAR_MINS;
        else
            return GRANULAR_SECS;
    }

    public BigDecimal getBaseValue(double value, int scale){
        return DateFormatter.getBaseTime(value, scale);
    }

    public BigDecimal getScaledValue(BigDecimal value, int targScale){
        return DateFormatter.getScaledTime(value, targScale);               
    }

    /**
     * Returns the # of seconds in a string in the form of "xx.xs" or
     * "xx:yy:zz.a"
     */
    private double parseTimeStr(String duration)
        throws ParseException
    {
        double nHours, nMins, nSecs;
        String[] vals;
        int idx;

        vals = (String[])StringUtil.explode(duration, 
                                            ":").toArray(new String[0]);

        if(vals.length != 3){
            throw new ParseException(duration, 0);
        }

        try {
            nHours = Double.parseDouble(vals[0]);
            nMins  = Double.parseDouble(vals[1]);
            nSecs  = Double.parseDouble(vals[2]);
            return nHours * 60 * 60 + nMins * 60 + nSecs;
        } catch(NumberFormatException exc){
            throw new ParseException(duration, 0);
        }
    }

    /**
     * Parse a string which is of the same format that we output, 
     * when outputting durations.
     */
    private UnitNumber parseRegular(String val, Locale locale,
                                    ParseSpecifics specifics)
        throws ParseException
    {
        final int unitType = UnitsConstants.UNIT_DURATION;
        UnitNumber res;
        String[] vals;

        vals = (String[])StringUtil.explode(val, " ").toArray(new String[0]);
        
        if(vals.length == 2 &&
           vals[0].charAt(vals[0].length() - 1) == 'y' &&
           vals[1].charAt(vals[1].length() - 1) == 'd')
        {
            try {
                String yStr = vals[0].substring(0, vals[0].length() - 1);
                String dStr = vals[1].substring(0, vals[1].length() - 1);

                return new UnitNumber(Integer.parseInt(yStr) * 365 + 
                                      Integer.parseInt(dStr), unitType,
                                      UnitsConstants.SCALE_DAY);
            } catch(NumberFormatException exc){
            }
        } else if(vals.length == 3 && 
                  (vals[1].equals("day") || vals[1].equals("days")))
        {
            try {
                return new UnitNumber(Integer.parseInt(vals[0]) * 24 * 60 * 60+
                                      this.parseTimeStr(vals[2]),
                                      unitType, UnitsConstants.SCALE_SEC);
            } catch(NumberFormatException exc){
            }
        } else if(vals.length == 1){
            return new UnitNumber(this.parseTimeStr(vals[0]), unitType,
                                  UnitsConstants.SCALE_SEC);
        }

        throw new ParseException(val, 0);
    }

    public UnitNumber parse(String val, Locale locale, 
                            ParseSpecifics specifics)
        throws ParseException
    {
        NumberFormat fmt = NumberFormat.getInstance(locale);
        double numberPart;
        String tagPart;
        int nonIdx, scale;

        try {
            return this.parseRegular(val, locale, specifics);
        } catch(ParseException exc){
            // That's fine, try another format
        }

        nonIdx = UnitsUtil.findNonNumberIdx(val, fmt);
        if(nonIdx == -1){
            throw new ParseException("Number had no units with it", 
                                     val.length());
        }

        if(nonIdx == 0){
            throw new ParseException("Invalid number specified", 0);
        }

        numberPart = fmt.parse(val.substring(0, nonIdx)).doubleValue();
        tagPart    = val.substring(nonIdx, val.length()).trim();

        if(tagPart.equalsIgnoreCase("y") ||
           tagPart.equalsIgnoreCase("yr") ||
           tagPart.equalsIgnoreCase("yrs") ||
           tagPart.equalsIgnoreCase("year") ||
           tagPart.equalsIgnoreCase("years"))
        {
            scale = UnitsConstants.SCALE_YEAR;
        } else if(tagPart.equalsIgnoreCase("w") ||
                  tagPart.equalsIgnoreCase("wk") ||
                  tagPart.equalsIgnoreCase("wks") ||
                  tagPart.equalsIgnoreCase("week") ||
                  tagPart.equalsIgnoreCase("weeks"))
        {
            scale = UnitsConstants.SCALE_WEEK;
        } else if(tagPart.equalsIgnoreCase("d") ||
                  tagPart.equalsIgnoreCase("day") ||
                  tagPart.equalsIgnoreCase("days"))
        {
            scale = UnitsConstants.SCALE_DAY;
        } else if(tagPart.equalsIgnoreCase("h") ||
                  tagPart.equalsIgnoreCase("hr") ||
                  tagPart.equalsIgnoreCase("hrs") ||
                  tagPart.equalsIgnoreCase("hour") ||
                  tagPart.equalsIgnoreCase("hours"))
        {
            scale = UnitsConstants.SCALE_HOUR;
        } else if(tagPart.equalsIgnoreCase("m") ||
                  tagPart.equalsIgnoreCase("min") ||
                  tagPart.equalsIgnoreCase("mins") ||
                  tagPart.equalsIgnoreCase("minute") ||
                  tagPart.equalsIgnoreCase("minutes"))
        {
            scale = UnitsConstants.SCALE_MIN;
        } else if(tagPart.equalsIgnoreCase("s") ||
                  tagPart.equalsIgnoreCase("sec") ||
                  tagPart.equalsIgnoreCase("secs") ||
                  tagPart.equalsIgnoreCase("second") ||
                  tagPart.equalsIgnoreCase("seconds"))
        {
            scale = UnitsConstants.SCALE_SEC;
        } else if(tagPart.equalsIgnoreCase("j") ||
                  tagPart.equalsIgnoreCase("jif") ||
                  tagPart.equalsIgnoreCase("jifs") ||
                  tagPart.equalsIgnoreCase("jiffy") ||
                  tagPart.equalsIgnoreCase("jiffys") ||
                  tagPart.equalsIgnoreCase("jifferoonies"))
        {
            scale = UnitsConstants.SCALE_JIFFY;
        } else if(tagPart.equalsIgnoreCase("ms") ||
                  tagPart.equalsIgnoreCase("milli") ||
                  tagPart.equalsIgnoreCase("millis") ||
                  tagPart.equalsIgnoreCase("millisecond") ||
                  tagPart.equalsIgnoreCase("milliseconds"))
        {
            scale = UnitsConstants.SCALE_MILLI;
        } else if(tagPart.equalsIgnoreCase("us") ||
                  tagPart.equalsIgnoreCase("micro") ||
                  tagPart.equalsIgnoreCase("micros") ||
                  tagPart.equalsIgnoreCase("microsecond") ||
                  tagPart.equalsIgnoreCase("microseconds"))
        {
            scale = UnitsConstants.SCALE_MICRO;
        } else if(tagPart.equalsIgnoreCase("ns") ||
                  tagPart.equalsIgnoreCase("nano") ||
                  tagPart.equalsIgnoreCase("nanos") ||
                  tagPart.equalsIgnoreCase("nanosecond") ||
                  tagPart.equalsIgnoreCase("nanoseconds"))
        {
            scale = UnitsConstants.SCALE_NANO;
        } else {
            throw new ParseException("Unknown duration '" + tagPart + "'",
                                     nonIdx);
        }

        return new UnitNumber(numberPart, UnitsConstants.UNIT_DURATION,
                              scale);
    }
}
