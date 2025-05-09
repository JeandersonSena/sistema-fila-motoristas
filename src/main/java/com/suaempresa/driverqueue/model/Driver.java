package com.suaempresa.driverqueue.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entidade JPA que representa um Motorista na fila.
 * Mapeada para a tabela "driver" no banco de dados.
 */
@Entity
public class Driver {

    /** Identificador único do motorista (Chave primária, auto-incrementada). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Placa do veículo do motorista (formato Mercosul ou antigo). Deve ser única. */
    @NotBlank(message = "A placa não pode estar em branco.")
    @Pattern(regexp = "^[A-Z]{3}-?\\d[A-Z0-9]\\d{2}$", message = "Formato de placa inválido (Ex: ABC-1234 ou ABC1D23).")
    @Column(nullable = false, unique = true)
    private String plate;

    /** Nome completo do motorista. */
    @NotBlank(message = "O nome não pode estar em branco.")
    @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres.")
    @Column(nullable = false)
    private String name;

    /** Número de telefone do motorista para contato via SMS (formato E.164). */
    @NotBlank(message = "O número de telefone não pode estar em branco.")
    @Pattern(regexp = "^\\+[1-9]\\d{10,14}$", message = "Formato de telefone inválido (Ex: +55119...).")
    @Column(nullable = false)
    private String phoneNumber;

    /** Data e hora exatas em que o motorista entrou na fila. */
    @Column(nullable = false, updatable = false) // Não deve ser alterado após criação
    private LocalDateTime entryTime;

    /** Data e hora exatas em que o motorista foi chamado pela última vez. */
    @Column
    private LocalDateTime calledTime;

    /** Status atual do motorista na fila. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriverStatus status;

    /** Número de vezes que este motorista foi chamado (para controle de re-chamadas). */
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int callAttempts = 0;

    /**
     * Enumeração representando os possíveis status de um motorista na fila.
     */
    public enum DriverStatus {
        WAITING, CALLED, CLEARED, ATTENDED, NO_SHOW
    }

    /** Construtor padrão sem argumentos exigido pelo JPA. */
    public Driver() {
    }

    // --- Getters e Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }
    public LocalDateTime getCalledTime() { return calledTime; }
    public void setCalledTime(LocalDateTime calledTime) { this.calledTime = calledTime; }
    public DriverStatus getStatus() { return status; }
    public void setStatus(DriverStatus status) { this.status = status; }
    public int getCallAttempts() { return callAttempts; }
    public void setCallAttempts(int callAttempts) { this.callAttempts = callAttempts; }

    // --- equals, hashCode, toString ---
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

    /**
     * Retorna uma representação textual do objeto Driver, útil para logs.
     * Inclui todos os campos principais, incluindo o número de tentativas de chamada.
     * @return String representando o objeto Driver.
     */
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
                ", callAttempts=" + callAttempts + // <-- Adicionado aqui
                '}';
    }
}