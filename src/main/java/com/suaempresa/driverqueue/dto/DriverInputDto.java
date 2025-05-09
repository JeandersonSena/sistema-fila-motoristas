package com.suaempresa.driverqueue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) usado para receber e validar os dados de entrada
 * do formulário de registro de motorista ({@code index.html}).
 * As anotações de validação garantem que os dados estejam corretos antes de serem
 * processados pelo Controller e Service.
 */
public class DriverInputDto {

    /** Placa do veículo fornecida pelo motorista. */
    @NotBlank(message = "A placa não pode estar em branco.")
    @Pattern(regexp = "^[A-Z]{3}-?\\d[A-Z0-9]\\d{2}$", message = "Formato de placa inválido (Ex: ABC-1234 ou ABC1D23).")
    private String plate;

    /** Nome fornecido pelo motorista. */
    @NotBlank(message = "O nome não pode estar em branco.")
    @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres.")
    private String name;

    /** Número de telefone fornecido pelo motorista (formato E.164 esperado). */
    @NotBlank(message = "O número de telefone não pode estar em branco.")
    @Pattern(regexp = "^(\\(\\d{2}\\)\\s?)?\\d{4,5}-?\\d{4}$|^\\d{10,11}$", message = "Formato de telefone inválido. Use (XX) XXXXX-XXXX ou XXXXXXXXXXX.")
    private String phoneNumber;

    // --- Getters e Setters ---
    // Necessários para binding do Spring MVC e acesso no Controller.

    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    // toString útil para logs no controller
    @Override
    public String toString() {
        return "DriverInputDto{" +
                "plate='" + plate + '\'' +
                ", name='" + name + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                '}';
    }
}