# WidgetWeatherSummary stores this enum in Moshi JSON. EnumJsonAdapter looks up
# each constant using Class.getField(enum.name()), so its field names must survive
# shrinking. Generated adapters, Room and WorkManager use their consumer rules.
-keepclassmembers enum com.tiandili.calendar.domain.weather.CacheFreshness {
    <fields>;
    **[] values();
}
