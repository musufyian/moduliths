/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moduliths.moments.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.moduliths.moments.DayHasPassed;
import org.moduliths.moments.HourHasPassed;
import org.moduliths.moments.MonthHasPassed;
import org.moduliths.moments.QuarterHasPassed;
import org.moduliths.moments.ShiftedQuarter;
import org.moduliths.moments.WeekHasPassed;
import org.moduliths.moments.YearHasPassed;
import org.moduliths.moments.support.MomentsProperties.Granularity;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for {@link Moments}.
 *
 * @author Oliver Drotbohm
 */
class MomentsUnitTests {

	ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	Clock clock = Clock.systemUTC();

	Moments hourly = new Moments(clock, events, MomentsProperties.DEFAULTS);
	Moments daily = new Moments(clock, events, MomentsProperties.DEFAULTS.withGranularity(Granularity.DAYS));

	@Test // #215
	void emitsHourlyEventOnTimeShift() {

		hourly.shiftBy(Duration.ofDays(1));

		verify(events, times(1)).publishEvent(any(DayHasPassed.class));
		verify(events, times(24)).publishEvent(any(HourHasPassed.class));
	}

	@Test // #215
	void onlyEmitsDailyEventOnTimeShiftIfConfigured() {

		daily.shiftBy(Duration.ofDays(1));

		verify(events, times(1)).publishEvent(any(DayHasPassed.class));
		verify(events, never()).publishEvent(any(HourHasPassed.class));
	}

	@Test // #215
	void emitsMonthHasPassedForShiftAcrossMonths() {

		LocalDate now = LocalDate.now();
		Duration shift = Duration.ofDays(now.lengthOfMonth());

		daily.shiftBy(shift);

		verify(events, times(now.lengthOfMonth())).publishEvent(any(DayHasPassed.class));
		verify(events, times(1)).publishEvent(any(MonthHasPassed.class));
	}

	@Test // #215
	void doesNotEmitAnyEventsOnNegativeTimeShift() {

		hourly.shiftBy(Duration.ofDays(-1));

		verifyNoInteractions(events);
	}

	@Test // #215
	void emitsHourHasPassedOnScheduledMethod() {

		hourly.everyHour();

		verify(events, times(1)).publishEvent(any(HourHasPassed.class));
	}

	@Test // #215
	void emitsDayHasPassedOnScheduledMethod() {

		hourly.everyMidnight();

		verify(events, times(1)).publishEvent(any(DayHasPassed.class));
	}

	@Test // #215
	void emitsWeekHasPassedIfWeekIsExceeded() {

		LocalDate now = LocalDate.now();
		int weekOfYear = now.get(WeekFields.of(Locale.getDefault()).weekOfYear());

		daily.shiftBy(Duration.ofDays(7));

		WeekHasPassed reference = WeekHasPassed.of(Year.from(now), weekOfYear, Locale.getDefault());

		verify(events, times(1)).publishEvent(eq(reference));
	}

	@Test // #215
	void emitsWeekHasPassedWithCustomLocaleIfConfigured() {

		Locale locale = Locale.GERMAN;
		MomentsProperties properties = MomentsProperties.DEFAULTS.withLocale(locale);

		LocalDate now = LocalDate.now();
		int weekOfYear = now.get(WeekFields.of(locale).weekOfYear());

		new Moments(clock, events, properties).shiftBy(Duration.ofDays(7));

		WeekHasPassed reference = WeekHasPassed.of(Year.from(now), weekOfYear, locale);

		verify(events, times(1)).publishEvent(eq(reference));
	}

	@Test // #215
	void emitsQuarterHasPassed() {

		LocalDate now = LocalDate.now();
		Duration duration = getNumberOfDaysForThreeMonth(now);

		ShiftedQuarter quarter = MomentsProperties.DEFAULTS //
				.withGranularity(Granularity.DAYS) //
				.getShiftedQuarter(now);

		daily.shiftBy(duration);

		QuarterHasPassed reference = QuarterHasPassed.of(Year.from(now), quarter);

		verify(events, times(1)).publishEvent(eq(reference));
	}

	@Test // #215
	void emitsYearHasPassed() {

		daily.shiftBy(Duration.ofDays(365));

		YearHasPassed reference = YearHasPassed.of(Year.now());

		verify(events, times(1)).publishEvent(eq(reference));
	}

	@Test // #215
	void shiftsTimeForDuration() {

		Duration duration = Duration.ofHours(4);
		LocalDateTime before = hourly.now();
		LocalDateTime after = hourly.shiftBy(duration).now();

		assertThat(before.plus(duration)).isCloseTo(after, within(200, ChronoUnit.MILLIS));
	}

	private Duration getNumberOfDaysForThreeMonth(LocalDate date) {

		int days = 0;

		for (int i = 0; i < 3; i++) {
			days += date.lengthOfMonth();
			date = date.plusDays(days);
		}

		return Duration.ofDays(days);
	}
}
