/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.utils;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.Nullable;
import android.text.format.DateFormat;
import com.waz.zclient.R;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.Date;

public class ZTimeFormatter {

    public static String getSeparatorTime(@Nullable Resources resources, LocalDateTime now, LocalDateTime then, boolean is24HourFormat, ZoneId timeZone, boolean epocIsJustNow) {
        return getSeparatorTime(resources, now, then, is24HourFormat, timeZone, epocIsJustNow, true);
    }

    public static String getSeparatorTime(@Nullable Resources resources, LocalDateTime now, LocalDateTime then, boolean is24HourFormat, ZoneId timeZone, boolean epocIsJustNow, boolean showWeekday) {
        if (resources == null) {
            return "";
        }

        final boolean isLastTwoMins = now.minusMinutes(2).isBefore(then) || (epocIsJustNow && then.atZone(timeZone).toInstant().toEpochMilli() == 0);
        final boolean isLastSixtyMins = now.minusMinutes(60).isBefore(then);

        if (isLastTwoMins) {
            return resources.getString(R.string.timestamp__just_now);
        } else if (isLastSixtyMins) {
            int minutes = (int) Duration.between(then, now).toMinutes();
            return resources.getQuantityString(R.plurals.timestamp__x_minutes_ago, minutes, minutes);
        }

        final String time = getTimeFormatString(resources, is24HourFormat);
        final boolean isSameDay = now.toLocalDate().atStartOfDay().isBefore(then);
        final boolean isThisYear = now.getYear() == then.getYear();
        final String pattern;
        if (isSameDay) {
            pattern = time;
        } else if (isThisYear) {
            if (showWeekday) {
                pattern = resources.getString(R.string.timestamp_pattern__date_and_time__no_year, time);
            } else {
                pattern = resources.getString(R.string.timestamp_pattern__date_and_time__no_year_no_weekday, time);
            }
        } else {
            if (showWeekday) {
                pattern = resources.getString(R.string.timestamp_pattern__date_and_time__with_year, time);
            } else {
                pattern = resources.getString(R.string.timestamp_pattern__date_and_time__with_year_no_weekday, time);
            }
        }
        return DateTimeFormatter.ofPattern(pattern).format(then.atZone(timeZone));
    }

    public static String getSingleMessageTime(Context context, Date date) {
        boolean is24HourFormat = DateFormat.is24HourFormat(context);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(getTimeFormatString(context.getResources(), is24HourFormat));
        return formatter.format(DateConvertUtils.asLocalDateTime(date).atZone(ZoneId.systemDefault()));
    }

    public static String getCurrentWeek(Context context) {
        String pattern = context.getResources().getString(R.string.timestamp_pattern__week);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return formatter.format(DateConvertUtils.asLocalDateTime(Instant.now()).atZone(ZoneId.systemDefault()));
    }

    private static String getTimeFormatString(@Nullable Resources resources, boolean is24HourFormat) {
        if (resources == null) {
            return "";
        }
        if (is24HourFormat) {
            return resources.getString(R.string.timestamp_pattern__24h_format);
        } else {
            return resources.getString(R.string.timestamp_pattern__12h_format);
        }
    }
}
