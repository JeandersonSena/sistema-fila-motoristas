package com.suaempresa.driverqueue.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

// Import estático para usar withDefaults() para o formulário de login
import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Configuração de segurança principal para a aplicação web Spring Boot.
 * Habilita a segurança web, define regras de autorização, configuração de login/logout
 * e gerenciamento de usuários (em memória para este exemplo).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Define a cadeia de filtros de segurança para as requisições HTTP.
     * Configura quais URLs são protegidas, como o login e logout são tratados,
     * e desabilita CSRF para simplificação neste exemplo.
     *
     * @param http O objeto HttpSecurity para configurar.
     * @return O SecurityFilterChain construído.
     * @throws Exception Se ocorrer um erro durante a configuração.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                .requestMatchers("/admin/**").hasRole("ADMIN") // URLs /admin/** exigem role ADMIN
                                .requestMatchers("/css/**", "/js/**", "/error").permitAll() // Permite acesso a recursos estáticos e página de erro
                                .anyRequest().permitAll() // Todas as outras URLs (ex: "/") são permitidas sem autenticação
                )
                // Configura o formulário de login para usar a página padrão gerada pelo Spring Security.
                // Isso evita a necessidade de criar um controller e template HTML customizados para /login.
                .formLogin(withDefaults()) // <--- MODIFICAÇÃO PRINCIPAL AQUI
                .logout(logout -> // Configura a funcionalidade de logout
                        logout
                                .logoutSuccessUrl("/") // Redireciona para a página inicial após logout bem-sucedido
                                .permitAll() // Permite acesso ao endpoint de logout
                )
                // DESABILITA CSRF: Para simplificar o exemplo.
                // ATENÇÃO: Para aplicações de produção, entenda as implicações de desabilitar CSRF
                // e considere usar a proteção CSRF padrão do Spring Security.
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    /**
     * Define um {@link UserDetailsService} que gerencia usuários em memória.
     * Útil para desenvolvimento e prototipagem. Em produção, geralmente se usa
     * um UserDetailsService que busca usuários de um banco de dados.
     *
     * @param passwordEncoder O {@link PasswordEncoder} a ser usado para codificar as senhas.
     * @return Um UserDetailsService com usuários pré-definidos.
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        // Usuário Administrador
        UserDetails adminUser = User.builder()
                .username("admin")
                // A senha "password" é codificada usando BCrypt.
                // IMPORTANTE: Em um cenário real, use senhas fortes e gere os hashes de forma segura.
                .password(passwordEncoder.encode("password"))
                .roles("ADMIN") // Atribui a role ADMIN, que é usada nas regras de autorização
                .build();

        // Usuário comum (exemplo, não usado para proteger rotas específicas ainda)
        UserDetails regularUser = User.builder()
                .username("user")
                .password(passwordEncoder.encode("user"))
                .roles("USER")
                .build();

        // Gerenciador de detalhes do usuário em memória
        return new InMemoryUserDetailsManager(adminUser, regularUser);
    }

    /**
     * Define o {@link PasswordEncoder} a ser usado na aplicação para codificar
     * e verificar senhas. BCrypt é o padrão recomendado.
     *
     * @return Uma instância de BCryptPasswordEncoder.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}