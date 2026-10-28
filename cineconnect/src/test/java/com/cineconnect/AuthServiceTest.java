package com.cineconnect;

import com.cineconnect.exception.AuthenticationException;
import com.cineconnect.model.Role;
import com.cineconnect.model.User;
import com.cineconnect.service.AuthService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    private final AuthService auth = TestSupport.newContext(new TestSupport.MutableClock()).getAuthService();

    @Test
    void registerThenLogin() {
        auth.register("sam", "pass1234", "Sam Jones");
        User u = auth.login("sam", "pass1234");
        assertEquals(Role.CUSTOMER, u.getRole());
        assertEquals("Sam Jones", u.getFullName());
    }

    @Test
    void adminGetsAdminRoleAndTitle() {
        auth.createAdmin("boss", "pass1234", "The Boss");
        User u = auth.login("boss", "pass1234");
        assertEquals(Role.ADMIN, u.getRole());
        assertTrue(u.getDashboardTitle().contains("Admin"));
    }

    @Test
    void wrongPasswordAndUnknownUserAreRejected() {
        auth.register("sam", "pass1234", "Sam");
        assertThrows(AuthenticationException.class, () -> auth.login("sam", "nope"));
        assertThrows(AuthenticationException.class, () -> auth.login("ghost", "pass1234"));
    }

    @Test
    void duplicateUsernameIsRejected() {
        auth.register("sam", "pass1234", "Sam");
        assertThrows(AuthenticationException.class, () -> auth.register("sam", "other123", "Other"));
    }
}
