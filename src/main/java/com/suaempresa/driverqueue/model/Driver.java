package com.suaempresa.driverqueue.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String licensePlate; // Placa

    @Column(nullable = false, length = 20) // Ajuste o tamanho conforme necessário
    private String phoneNumber; // Número de telefone (formato E.164 recomendado para Twilio: +55119...)

    @Column(nullable = false, updatable = false)
    private LocalDateTime entryTimestamp; // Hora de Entrada

    @Column(nullable = false, unique = true) // Número único na fila ATIVA
    private Long sequenceNumber; // Número sequencial na fila

    @Column(nullable = false, length = 20)
    private String status; // WAITING, CALLED

    @Column // Pode ser nulo até ser chamado
    private LocalDateTime callTimestamp; // Hora da chamada

    // Construtor padrão
    public Driver() {
        this.entryTimestamp = LocalDateTime.now();
        this.status = "WAITING";
    }

    // Construtor para criação
    public Driver(String licensePlate, String phoneNumber, Long sequenceNumber) {
        this(); // Chama construtor padrão
        this.licensePlate = licensePlate;
        this.phoneNumber = phoneNumber; // Armazenar com +código_país? Validar formato?
        this.sequenceNumber = sequenceNumber;
    }

    // --- Getters e Setters ---
    // (Gerar com Alt+Insert no IntelliJ)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public LocalDateTime getEntryTimestamp() { return entryTimestamp; }
    public void setEntryTimestamp(LocalDateTime entryTimestamp) { this.entryTimestamp = entryTimestamp; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCallTimestamp() { return callTimestamp; }
    public void setCallTimestamp(LocalDateTime callTimestamp) { this.callTimestamp = callTimestamp; }

    @Override
    public String toString() {
        return "Driver{" + "id=" + id + ", plate='" + licensePlate + '\'' + ", phone='" + phoneNumber + '\'' + ", seq=" + sequenceNumber + ", status='" + status + '\'' + '}';
    }
}