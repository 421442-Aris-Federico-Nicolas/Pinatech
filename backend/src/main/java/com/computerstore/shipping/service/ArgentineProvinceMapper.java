package com.computerstore.shipping.service;

import java.text.Normalizer;
import java.util.Map;
import com.computerstore.common.exception.InvalidRequestException;

public final class ArgentineProvinceMapper {
    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("C", "Capital Federal"), Map.entry("CABA", "Capital Federal"),
            Map.entry("CAPITAL FEDERAL", "Capital Federal"), Map.entry("CIUDAD AUTONOMA DE BUENOS AIRES", "Capital Federal"),
            Map.entry("B", "Buenos Aires"), Map.entry("BA", "Buenos Aires"), Map.entry("BUENOS AIRES", "Buenos Aires"),
            Map.entry("K", "Catamarca"), Map.entry("CATAMARCA", "Catamarca"), Map.entry("H", "Chaco"),
            Map.entry("CHACO", "Chaco"), Map.entry("U", "Chubut"), Map.entry("CHUBUT", "Chubut"),
            Map.entry("X", "Córdoba"), Map.entry("CORDOBA", "Córdoba"), Map.entry("W", "Corrientes"),
            Map.entry("CORRIENTES", "Corrientes"), Map.entry("E", "Entre Ríos"), Map.entry("ENTRE RIOS", "Entre Ríos"),
            Map.entry("P", "Formosa"), Map.entry("FORMOSA", "Formosa"), Map.entry("Y", "Jujuy"), Map.entry("JUJUY", "Jujuy"),
            Map.entry("L", "La Pampa"), Map.entry("LA PAMPA", "La Pampa"), Map.entry("F", "La Rioja"),
            Map.entry("LA RIOJA", "La Rioja"), Map.entry("M", "Mendoza"), Map.entry("MENDOZA", "Mendoza"),
            Map.entry("N", "Misiones"), Map.entry("MISIONES", "Misiones"), Map.entry("Q", "Neuquén"),
            Map.entry("NEUQUEN", "Neuquén"), Map.entry("R", "Río Negro"), Map.entry("RIO NEGRO", "Río Negro"),
            Map.entry("A", "Salta"), Map.entry("SALTA", "Salta"), Map.entry("J", "San Juan"), Map.entry("SAN JUAN", "San Juan"),
            Map.entry("D", "San Luis"), Map.entry("SAN LUIS", "San Luis"), Map.entry("Z", "Santa Cruz"),
            Map.entry("SANTA CRUZ", "Santa Cruz"), Map.entry("S", "Santa Fe"), Map.entry("SANTA FE", "Santa Fe"),
            Map.entry("G", "Santiago del Estero"), Map.entry("SANTIAGO DEL ESTERO", "Santiago del Estero"),
            Map.entry("V", "Tierra del Fuego"), Map.entry("TIERRA DEL FUEGO", "Tierra del Fuego"),
            Map.entry("T", "Tucumán"), Map.entry("TUCUMAN", "Tucumán"));
    private ArgentineProvinceMapper() {}
    public static String name(String supplied) {
        String key = Normalizer.normalize(supplied == null ? "" : supplied.trim().toUpperCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceFirst("^AR-", "");
        String value = NAMES.get(key);
        if (value == null) throw new InvalidRequestException("The address province is not recognized for Argentina.");
        return value;
    }
}
