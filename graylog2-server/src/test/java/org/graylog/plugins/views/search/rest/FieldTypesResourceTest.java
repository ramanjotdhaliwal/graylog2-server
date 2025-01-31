/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package org.graylog.plugins.views.search.rest;

import com.google.common.collect.ImmutableSet;
import org.graylog.plugins.views.search.permissions.SearchUser;
import org.graylog2.indexer.fieldtypes.FieldTypes;
import org.graylog2.indexer.fieldtypes.MappedFieldTypesService;
import org.graylog2.plugin.indexer.searches.timeranges.RelativeRange;
import org.graylog2.plugin.indexer.searches.timeranges.TimeRange;
import org.graylog2.shared.rest.exceptions.MissingStreamPermissionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FieldTypesResourceTest {
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private MappedFieldTypesService mappedFieldTypesService;

    @Mock
    private PermittedStreams permittedStreams;

    @Captor
    private ArgumentCaptor<Set<String>> streamIdCaptor;

    @Captor
    private ArgumentCaptor<TimeRange> timeRangeArgumentCaptor;

    private FieldTypesResource fieldTypesResource;

    @Before
    public void setUp() throws Exception {
        this.fieldTypesResource = new FieldTypesResource(mappedFieldTypesService);
    }

    @Test
    public void allFieldTypesChecksPermissionsForStream() {

        final SearchUser searchUser = TestSearchUser.builder()
                .allowStream("4242")
                .denyStream("2323")
                .build();

        final MappedFieldTypesService dependency = Mockito.mock(MappedFieldTypesService.class);
        Mockito.when(dependency.fieldTypesByStreamIds(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            final Collection<String> streamIDs = invocation.getArgument(0);
            // for each streamID return a field that's called exactly like the streamID
            return streamIDs.stream().map(streamID -> MappedFieldTypeDTO.create(streamID, FieldTypes.Type.builder().type("text").build())).collect(Collectors.toSet());
        });

        final FieldTypesResource resource = new FieldTypesResource(dependency);
        final Set<MappedFieldTypeDTO> fields = resource.allFieldTypes(searchUser);

        // field for allowed stream has to be present
        assertThat(fields.stream().anyMatch(f -> f.name().equals("4242"))).isTrue();

        // field for denied stream must not be present
        assertThat(fields.stream().anyMatch(f -> f.name().equals("2323"))).isFalse();
    }

    @Test
    public void allFieldTypesReturnsResultFromMappedFieldTypesService() {
        final SearchUser searchUser = TestSearchUser.builder()
                .allowStream("2323")
                .allowStream("4242")
                .build();

        when(permittedStreams.load(any())).thenReturn(ImmutableSet.of("2323", "4242"));

        final Set<MappedFieldTypeDTO> fieldTypes = Collections.singleton(MappedFieldTypeDTO.create("foobar",
                FieldTypes.Type.createType("long", ImmutableSet.of("numeric", "enumerable"))));
        when(mappedFieldTypesService.fieldTypesByStreamIds(eq(ImmutableSet.of("2323", "4242")), eq(RelativeRange.allTime()))).thenReturn(fieldTypes);

        final Set<MappedFieldTypeDTO> result = this.fieldTypesResource.allFieldTypes(searchUser);

        assertThat(result).isEqualTo(fieldTypes);
    }

    @Test
    public void passesRequestedTimeRangeToMappedFieldTypesService() throws Exception {
        final SearchUser searchUser = TestSearchUser.builder()
                .allowStream("2323")
                .allowStream("4242")
                .build();

        final FieldTypesForStreamsRequest request = FieldTypesForStreamsRequest.Builder.builder()
                .streams(ImmutableSet.of("2323", "4242"))
                .timerange(RelativeRange.create(300))
                .build();

        this.fieldTypesResource.byStreams(request, searchUser);

        verify(this.mappedFieldTypesService, times(1)).fieldTypesByStreamIds(streamIdCaptor.capture(), timeRangeArgumentCaptor.capture());

        assertThat(timeRangeArgumentCaptor.getValue()).isEqualTo(RelativeRange.create(300));
    }

    @Test
    public void byStreamChecksPermissionsForStream() {
        final SearchUser searchUser = TestSearchUser.builder()
                .allowStream("2323")
                .allowStream("4242")
                .build();


        final MappedFieldTypesService dependency = Mockito.mock(MappedFieldTypesService.class);
        Mockito.when(dependency.fieldTypesByStreamIds(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            final Collection<String> streamIDs = invocation.getArgument(0);
            // for each streamID return a field that's called exactly like the streamID
            return streamIDs.stream().map(streamID -> MappedFieldTypeDTO.create(streamID, FieldTypes.Type.builder().type("text").build())).collect(Collectors.toSet());
        });

        final FieldTypesResource resource = new FieldTypesResource(dependency);

        final Set<MappedFieldTypeDTO> fields = resource.byStreams(
                FieldTypesForStreamsRequest.Builder.builder()
                        .streams(ImmutableSet.of("2323", "4242"))
                        .build(),
                searchUser
        );

        // field for allowed stream has to be present
        assertThat(fields.stream().anyMatch(f -> f.name().equals("2323"))).isTrue();
        assertThat(fields.stream().anyMatch(f -> f.name().equals("4242"))).isTrue();


    }

    @Test
    public void byStreamReturnsTypesFromMappedFieldTypesService() {

        final SearchUser searchUser = TestSearchUser.builder()
                .allowStream("2323")
                .allowStream("4242")
                .build();

        final Set<MappedFieldTypeDTO> fieldTypes = Collections.singleton(MappedFieldTypeDTO.create("foobar",
                FieldTypes.Type.createType("long", ImmutableSet.of("numeric", "enumerable"))));
        when(mappedFieldTypesService.fieldTypesByStreamIds(eq(ImmutableSet.of("2323", "4242")), eq(RelativeRange.allTime()))).thenReturn(fieldTypes);

        final Set<MappedFieldTypeDTO> result = this.fieldTypesResource.byStreams(
                FieldTypesForStreamsRequest.Builder.builder()
                        .streams(ImmutableSet.of("2323", "4242"))
                        .build(),
                searchUser
        );

        verify(mappedFieldTypesService, times(1)).fieldTypesByStreamIds(streamIdCaptor.capture(), timeRangeArgumentCaptor.capture());

        assertThat(streamIdCaptor.getValue()).containsExactlyInAnyOrder("2323", "4242");
        assertThat(timeRangeArgumentCaptor.getValue()).isEqualTo(RelativeRange.allTime());
        assertThat(result).isEqualTo(fieldTypes);
    }

    @Test
    public void shouldNotAllowAccessWithoutPermission() {
        final SearchUser searchUser = TestSearchUser
                .builder()
                .denyStream("2323")
                .allowStream("4242")
                .build();

        assertThatExceptionOfType(MissingStreamPermissionException.class)
                .isThrownBy(() -> fieldTypesResource.byStreams(
                        FieldTypesForStreamsRequest.Builder.builder()
                                .streams(ImmutableSet.of("2323", "4242"))
                                .build(),
                        searchUser
                ))
                .satisfies(ex -> assertThat(ex.streamsWithMissingPermissions()).contains("2323"));
    }

}
