package org.jahia.community.token.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.jahia.community.token.graphql.SupportTokenMutation;
import org.jahia.community.token.graphql.SupportTokenQuery;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.junit.Test;

/**
 * Karaf shell commands are UNGATED (Gap G1 / spec S13 / undocumented behaviour U14).
 *
 * <p>The GraphQL and React surfaces of this module both enforce the {@code supportTokenAdmin}
 * permission (and additionally refuse token-authenticated sessions). The Karaf shell surface
 * enforces <em>neither</em>: anyone with a Karaf shell can mint a login token for any user,
 * including {@code root}. This test pins that asymmetry so a future Stage-7 decision (add a gate,
 * or explicitly document the shell as a privileged trust boundary) is made deliberately rather
 * than by accident.
 *
 * <p>The Karaf shell cannot be reached from the Cypress container (no docker socket / no Karaf
 * access), so this control gap is proven here at the unit level instead of in {@code .cy.ts}:
 * <ol>
 *   <li><b>Behavioural</b> — each command's {@code execute()} runs its full business logic with
 *       <em>no</em> Jahia authorization, permission or session context established. A gated
 *       surface would refuse before doing any work; these commands proceed and return normally.</li>
 *   <li><b>Structural</b> — by reflection: the three GraphQL resolver methods carry
 *       {@code @GraphQLRequiresPermission("supportTokenAdmin")}, whereas the command classes carry
 *       no authorization annotation and expose no session/permission/request seam at all.</li>
 * </ol>
 *
 * <p>GENUINE-GAP (Stage-7 fix candidate).
 */
public class SupportTokenCommandGateTest {

    private static final String EXPECTED_PERMISSION = "supportTokenAdmin";

    // --- Behavioural: commands run with zero authorization context ---

    @Test
    public void createCommand_runsWithNoAuthorizationContext() throws Exception {
        // No username/recipient supplied and, crucially, no Jahia security context of any kind.
        // The command still executes and returns the empty-string sentinel rather than being
        // rejected by any permission/session gate — proving the shell surface is ungated.
        CreateCommand command = new CreateCommand();

        Object result = command.execute();

        assertThat(result).isEqualTo("");
    }

    @Test
    public void listCommand_runsWithNoAuthorizationContext() throws Exception {
        ListCommand command = new ListCommand();

        Object result = command.execute();

        assertThat(result).isNotNull();
        assertThat(result.toString()).isEmpty();
    }

    @Test
    public void clearCommand_runsWithNoAuthorizationContext() throws Exception {
        ClearCommand command = new ClearCommand();

        Object result = command.execute();

        // ClearCommand returns null; the point is that execute() completed without any
        // authorization check throwing.
        assertThat(result).isNull();
    }

    // --- Structural: the GraphQL surface IS gated (the contrast that makes the gap meaningful) ---

    @Test
    public void graphqlResolvers_areGatedBySupportTokenAdminPermission() throws Exception {
        assertGated(SupportTokenMutation.class, "createToken");
        assertGated(SupportTokenMutation.class, "clearAllTokens");
        assertGated(SupportTokenQuery.class, "listTokens");
    }

    private static void assertGated(Class<?> type, String methodName) {
        Method method = findMethod(type, methodName);
        GraphQLRequiresPermission annotation = method.getAnnotation(GraphQLRequiresPermission.class);
        assertThat(annotation)
                .as("%s.%s must be permission-gated", type.getSimpleName(), methodName)
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(EXPECTED_PERMISSION);
    }

    private static Method findMethod(Class<?> type, String methodName) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new AssertionError("Method not found: " + type.getName() + "#" + methodName);
    }

    // --- Structural: the command surface is NOT gated and exposes no security seam ---

    @Test
    public void commands_carryNoPermissionAnnotationAndNoSecuritySeam() throws Exception {
        for (Class<?> commandType : new Class<?>[]{CreateCommand.class, ListCommand.class, ClearCommand.class}) {
            // No authorization annotation anywhere on the class.
            assertThat(commandType.isAnnotationPresent(GraphQLRequiresPermission.class))
                    .as("%s must not (today) declare a permission gate", commandType.getSimpleName())
                    .isFalse();

            // execute() takes no injected security/session context and carries no gate annotation.
            Method execute = commandType.getDeclaredMethod("execute");
            assertThat(execute.getParameterCount())
                    .as("%s.execute() takes no injected authorization context", commandType.getSimpleName())
                    .isZero();
            assertThat(execute.isAnnotationPresent(GraphQLRequiresPermission.class)).isFalse();

            // No field references a request/session/permission-checking type.
            for (Field field : commandType.getDeclaredFields()) {
                Class<?> fieldType = field.getType();
                assertThat(fieldType)
                        .as("%s.%s must not be a session/request seam", commandType.getSimpleName(), field.getName())
                        .isNotIn(HttpServletRequest.class, HttpSession.class);
            }
        }
    }
}
