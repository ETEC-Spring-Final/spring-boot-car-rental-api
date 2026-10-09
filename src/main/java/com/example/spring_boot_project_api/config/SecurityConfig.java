package com.example.spring_boot_project_api.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private String corsAllowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler,
            OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler,
            CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository,
            JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Preflight requests must always be allowed through, unauthenticated,
                        // before any other rule is evaluated.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/bakong/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()

                        // ===== Public browsing endpoints (Explore / vehicle detail pages
                        // used before login) — must stay in sync with router/index.js
                        // routes that have no requiresAuth meta. =====
                        .requestMatchers(HttpMethod.GET, "/api/vehicles/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/locations/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/vehicle/**").permitAll()

                        // FIX (Phase A): the public vehicle detail page and the fleet grid
                        // on /home render car photos through the attachment flow. Without
                        // this rule a logged-out visitor gets 401 on every image lookup and
                        // the whole catalogue shows grey placeholder boxes.
                        // Writes (POST/PUT/DELETE) stay ADMIN/MANAGER via @PreAuthorize on
                        // VehicleImageController.
                        .requestMatchers(HttpMethod.GET, "/api/vehicle-images/**").permitAll()

                        // FIX (Phase A): brand chips on /home and the brand filter on
                        // /explore are rendered before login. Read is public, writes stay
                        // staff-only via @PreAuthorize on BrandController.
                        .requestMatchers(HttpMethod.GET, "/api/brands/**").permitAll()

                        // FIX (Phase A): the add-on services catalogue is shown on the
                        // public landing page and inside the booking form. Read is public;
                        // writes are now gated on ServiceController.
                        .requestMatchers(HttpMethod.GET, "/api/services/**").permitAll()

                        // Site branding/contact info (logo, site name, contact email/phone) —
                        // must be readable WITHOUT a JWT: the login page, the public
                        // marketing site header/footer, and the admin dashboard sidebar/
                        // header all render this before or without authentication.
                        // Updating it (PUT) still requires ADMIN/MANAGER via @PreAuthorize
                        // on SiteSettingsController.
                        .requestMatchers(HttpMethod.GET, "/api/settings").permitAll()

                        // "my-reviews" must stay authenticated even though it sits under
                        // /api/reviews — list it BEFORE the generic single-review rule
                        // below so it is matched first.
                        .requestMatchers(HttpMethod.GET, "/api/reviews/my-reviews").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/*").permitAll()

                        .requestMatchers("/api/notifications/device").authenticated()

                        // Everything else (notifications, /api/auth/users, all
                        // POST/PUT/PATCH/DELETE, dashboard data, etc.) requires a valid,
                        // authenticated JWT. Role-specific restrictions are still
                        // enforced separately via @PreAuthorize on each method.
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                        // FIX: the OAuth2 "state"/authorization-request storage moves from
                        // the (disabled) HttpSession to a short-lived HttpOnly cookie — see
                        // CookieOAuth2AuthorizationRequestRepository for why the previous
                        // session-backed default caused every Google/Facebook login to end
                        // in a 401 at /login/oauth2/code/{provider}.
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestRepository(cookieAuthorizationRequestRepository))
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> response
                                .sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                        .accessDeniedHandler((request, response, accessDeniedException) -> response
                                .sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Originated from APP_CORS_ALLOWED_ORIGINS (comma-separated), defaulting to
        // the local dev server. Cannot be "*" because credentials (JWT) are used.
        configuration.setAllowedOrigins(Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        configuration
                .setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}