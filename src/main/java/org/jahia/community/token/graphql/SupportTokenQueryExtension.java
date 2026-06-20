package org.jahia.community.token.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
@GraphQLDescription("Support token queries")
public class SupportTokenQueryExtension {

    private SupportTokenQueryExtension() {
    }

    @GraphQLField
    @GraphQLName("supportToken")
    @GraphQLDescription("Support token query namespace")
    public static SupportTokenQuery supportToken() {
        return new SupportTokenQuery();
    }
}
