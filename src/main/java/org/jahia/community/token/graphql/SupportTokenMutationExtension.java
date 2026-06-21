package org.jahia.community.token.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLDescription("Support token mutations")
public class SupportTokenMutationExtension {

    private SupportTokenMutationExtension() {
    }

    @GraphQLField
    @GraphQLName("supportToken")
    @GraphQLDescription("Support token mutation namespace")
    public static SupportTokenMutation supportToken() {
        return new SupportTokenMutation();
    }
}
