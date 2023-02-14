package de.rpicloud.ipv64net

enum class LaunchScreens {
    LOGIN , MAIN
}

enum class  ConsumptionType(val CType: Int) {
    Gas(1),
    Strom(2),
    Kaltwasser(3),
    Warmwasser(4),
    Muell(5);
}

enum class ConsumptionUnit(val Unit: String) {
    KubikUnit("m³"),
    StromUnit("kWh"),
    MuellUnit("x" );
}