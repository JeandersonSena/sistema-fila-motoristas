package com.suaempresa.driverqueue.model;

import jakarta.persistence.*; // Certifique-se de usar jakarta.persistence se for Spring Boot 3+
import java.time.LocalDateTime;
import java.util.Objects;

@Entity // Marca esta classe como uma tabela no banco de dados
public class Driver {

    @Id // Chave primária
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-incremento
    private Long id;

    @Column(nullable = false) // Não pode ser nulo no banco
    private String plate;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false) // Adicionado campo para telefone
    private String phoneNumber;

    @Column(nullable = false)
    private LocalDateTime entryTime; // Horário de entrada na fila

    private LocalDateTime calledTime; // Horário em que foi chamado (pode ser nulo)

    @Enumerated(EnumType.STRING) // Salva o nome do enum (WAITING, CALLED, CLEARED) no banco
    @Column(nullable = false)
    private DriverStatus status;

    // Enum para os status possíveis
    public enum DriverStatus {
        WAITING, // Aguardando na fila
        CALLED,  // Já foi chamado
        CLEARED  // Removido da fila pelo admin (sem ser chamado)
    }

    // --- Construtores ---
    public Driver() {
        // Construtor padrão exigido pelo JPA
    }

    // --- Getters e Setters ---
    // (Gerados automaticamente pela IDE ou escritos manualmente)

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlate() {
        return plate;
    }

    public void setPlate(String plate) {
        this.plate = plate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(LocalDateTime entryTime) {
        this.entryTime = entryTime;
    }

    public LocalDateTime getCalledTime() {
        return calledTime;
    }

    public void setCalledTime(LocalDateTime calledTime) {
        this.calledTime = calledTime;
    }

    public DriverStatus getStatus() {
        return status;
    }

    public void setStatus(DriverStatus status) {
        this.status = status;
    }

    // --- equals, hashCode, toString (Opcional, mas bom para debug) ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Driver driver = (Driver) o;
        return Objects.equals(id, driver.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Driver{" +
                "id=" + id +
                ", plate='" + plate + '\'' +
                ", name='" + name + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", entryTime=" + entryTime +
                ", calledTime=" + calledTime +
                ", status=" + status +
                '}';
    }
}