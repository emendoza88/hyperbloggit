package sura.gfsretiros.usecase.socio;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import sura.gfsretiros.domain.common.EventsGateway;
import sura.gfsretiros.domain.common.gateway.MessageLogGateway;
import sura.gfsretiros.domain.retiro.SolicitudRetiro;
import sura.gfsretiros.domain.retiro.error.Error;
import sura.gfsretiros.domain.retiro.estado.Estado;
import sura.gfsretiros.domain.retiro.estado.EstadoRetiro;
import sura.gfsretiros.domain.retiro.estado.Etapa;
import sura.gfsretiros.domain.retiro.estadocuenta.gateway.EstadoCuentaGateway;
import sura.gfsretiros.domain.retiro.events.ErrorActualizar;
import sura.gfsretiros.domain.retiro.events.RetiroSolicitadoInicio;
import sura.gfsretiros.domain.retiro.gateway.SolicitudRetiroRepository;
import sura.gfsretiros.domain.retiro.socio.ClaseVinculacion;
import sura.gfsretiros.domain.retiro.socio.Nomina;
import sura.gfsretiros.domain.retiro.socio.Socio;
import sura.gfsretiros.usecase.error.ErrorRetiroUseCase;

import java.util.Date;

import static org.mockito.ArgumentMatchers.*;

@RunWith(MockitoJUnitRunner.class)
public class ConsultarEstadoCuentaSocioUseCaseTest {

    @InjectMocks
    private ConsultarEstadoCuentaSocioUseCase useCase;

    @Mock
    private MessageLogGateway messageLogGateway;

    @Mock
    private EventsGateway eventBus;

    @Mock
    private EstadoCuentaGateway estadoCuentaGateway;

    @Mock
    private SolicitudRetiroRepository solicitudRetiroRepository;

    @Mock
    private ErrorRetiroUseCase errorRetiroUseCase;

    private SolicitudRetiro solicitudRetiro;

    private Socio socio;

    @Before
    public void setUp() {
        useCase = new ConsultarEstadoCuentaSocioUseCase(messageLogGateway, eventBus, estadoCuentaGateway, solicitudRetiroRepository, errorRetiroUseCase);

        Nomina nomina = Nomina.builder()
                .idNomina("1")
                .claseVinculacion(ClaseVinculacion.EMPLEADO)
                .build();

        socio = Socio.builder()
                .feIngresoFondo(new Date())
                .nombre1("nombre1")
                .nombre2("nombre2")
                .apellido1("apellido1")
                .apellido2("apellido2")
                .codigoSocio("10")
                .dniSocio("C123456")
                .nomina(nomina)
                .build();

        solicitudRetiro = SolicitudRetiro.builder()
                .nmSolicitud(1L)
                .estado(EstadoRetiro.builder()
                        .estado(Estado.SOLICITADO)
                        .etapa(Etapa.INGRESO)
                        .build())
                .tipoIdentificacion("C")
                .nmIdentificacion("123456")
                .feRetiroNomina(new Date())
                .socio(socio)
                .build();
    }

    @Test
    public void emitirEventoRetiroSolicitadoInicio() {
        Mockito.when(estadoCuentaGateway.consultarEstadoCuentaSocio(anyString(), anyString(), any()))
                .thenReturn(Mono.just(socio));

        Mockito.when(solicitudRetiroRepository.actualizarSolicitudRetiro(any()))
                .thenReturn(Mono.just(solicitudRetiro));

        Mockito.when(eventBus.emit(Mockito.any(RetiroSolicitadoInicio.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.emitirEventoRetiroSolicitadoInicio(solicitudRetiro))
                .verifyComplete();

        Mockito.verify(eventBus).emit(Mockito.any(RetiroSolicitadoInicio.class));

        Mockito.verify(estadoCuentaGateway).consultarEstadoCuentaSocio(solicitudRetiro.getTipoIdentificacion(), solicitudRetiro.getNmIdentificacion(), solicitudRetiro.getFeRetiroNomina());
    }

    @Test
    public void emitirEventoRetiroSolicitadoInicioFallido() {
        Mockito.when(estadoCuentaGateway.consultarEstadoCuentaSocio(anyString(), anyString(), any()))
                .thenReturn(Mono.just(socio));

        Mockito.when(solicitudRetiroRepository.actualizarSolicitudRetiro(any()))
                .thenReturn(Mono.just(solicitudRetiro));

        Mockito.when(eventBus.emit(Mockito.any(RetiroSolicitadoInicio.class))).thenReturn(Mono.error(new Exception()));

        Mockito.when(eventBus.emit(Mockito.any(ErrorActualizar.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.emitirEventoRetiroSolicitadoInicio(solicitudRetiro))
                .verifyComplete();

        Mockito.verify(eventBus).emit(Mockito.any(ErrorActualizar.class));

        Mockito.verify(estadoCuentaGateway).consultarEstadoCuentaSocio(solicitudRetiro.getTipoIdentificacion(), solicitudRetiro.getNmIdentificacion(), solicitudRetiro.getFeRetiroNomina());
    }

    @Test
    public void emitirEventoRetiroSolicitadoInicioSocioNoExiste() {
        Mockito.when(estadoCuentaGateway.consultarEstadoCuentaSocio(anyString(), anyString(), any()))
                .thenReturn(Mono.just(Socio.builder().build()));

        SolicitudRetiro solicitud = solicitudRetiro.toBuilder()
                .estado(EstadoRetiro.builder().estado(Estado.RECHAZADO).etapa(Etapa.INGRESO).build())
                .build();

        Mockito.when(solicitudRetiroRepository.actualizarSolicitudRetiro(any()))
                .thenReturn(Mono.just(solicitud));

        Mockito.when(errorRetiroUseCase.guardarError(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(Mono.just(Error.builder().build()));

        StepVerifier.create(useCase.emitirEventoRetiroSolicitadoInicio(solicitud))
                .verifyComplete();

    }

    @Test
    public void emitirEventoRetiroSolicitadoInicioDniSocioEmpty() {
        Mockito.when(estadoCuentaGateway.consultarEstadoCuentaSocio(anyString(), anyString(), any()))
                .thenReturn(Mono.just(socio.toBuilder().dniSocio("").build()));

        Mockito.when(solicitudRetiroRepository.actualizarSolicitudRetiro(any()))
                .thenReturn(Mono.just(solicitudRetiro));

        Mockito.when(errorRetiroUseCase.guardarError(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(Mono.just(Error.builder().build()));

        Mockito.when(eventBus.emit(Mockito.any(RetiroSolicitadoInicio.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.emitirEventoRetiroSolicitadoInicio(solicitudRetiro))
                .verifyComplete();

        Mockito.verify(eventBus).emit(Mockito.any(RetiroSolicitadoInicio.class));

    }

    @Test
    public void emitirEventoRetiroSolicitadoInicioDniSocioNull() {
        Mockito.when(estadoCuentaGateway.consultarEstadoCuentaSocio(anyString(), anyString(), any()))
                .thenReturn(Mono.just(socio.toBuilder().dniSocio(null).nomina(Nomina.builder().build()).build()));

        Mockito.when(solicitudRetiroRepository.actualizarSolicitudRetiro(any()))
                .thenReturn(Mono.just(solicitudRetiro));

        Mockito.when(errorRetiroUseCase.guardarError(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(Mono.just(Error.builder().build()));

        Mockito.when(eventBus.emit(Mockito.any(RetiroSolicitadoInicio.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.emitirEventoRetiroSolicitadoInicio(solicitudRetiro))
                .verifyComplete();

        Mockito.verify(eventBus).emit(Mockito.any(RetiroSolicitadoInicio.class));

    }

    @Test
    public void emitirEventoRetiroSolicitadoInicioSinNomina() {
        Mockito.when(estadoCuentaGateway.consultarEstadoCuentaSocio(anyString(), anyString(), any()))
                .thenReturn(Mono.just(socio.toBuilder().nomina(Nomina.builder().build()).build()));

        Mockito.when(solicitudRetiroRepository.actualizarSolicitudRetiro(any()))
                .thenReturn(Mono.just(solicitudRetiro));

        Mockito.when(errorRetiroUseCase.guardarError(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(Mono.just(Error.builder().build()));

        Mockito.when(eventBus.emit(Mockito.any(RetiroSolicitadoInicio.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.emitirEventoRetiroSolicitadoInicio(solicitudRetiro))
                .verifyComplete();

        Mockito.verify(eventBus).emit(Mockito.any(RetiroSolicitadoInicio.class));

    }

    @Test
    public void emitirEventoRetiroSolicitadoInicioSinClaseVinculacion() {
        Mockito.when(estadoCuentaGateway.consultarEstadoCuentaSocio(anyString(), anyString(), any()))
                .thenReturn(Mono.just(socio.toBuilder().nomina(Nomina.builder().idNomina("1").claseVinculacion(null).build()).build()));

        Mockito.when(solicitudRetiroRepository.actualizarSolicitudRetiro(any()))
                .thenReturn(Mono.just(solicitudRetiro));

        Mockito.when(errorRetiroUseCase.guardarError(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(Mono.just(Error.builder().build()));

        Mockito.when(eventBus.emit(Mockito.any(RetiroSolicitadoInicio.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.emitirEventoRetiroSolicitadoInicio(solicitudRetiro))
                .verifyComplete();

        Mockito.verify(eventBus).emit(Mockito.any(RetiroSolicitadoInicio.class));

    }

    @Test
    public void emitirEventoRetiroSolicitadoInicioIdNominaEmpty() {
        Mockito.when(estadoCuentaGateway.consultarEstadoCuentaSocio(anyString(), anyString(), any()))
                .thenReturn(Mono.just(socio.toBuilder().nomina(Nomina.builder().idNomina("").build()).build()));

        Mockito.when(solicitudRetiroRepository.actualizarSolicitudRetiro(any()))
                .thenReturn(Mono.just(solicitudRetiro));

        Mockito.when(errorRetiroUseCase.guardarError(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(Mono.just(Error.builder().build()));

        Mockito.when(eventBus.emit(Mockito.any(RetiroSolicitadoInicio.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.emitirEventoRetiroSolicitadoInicio(solicitudRetiro))
                .verifyComplete();

        Mockito.verify(eventBus).emit(Mockito.any(RetiroSolicitadoInicio.class));

    }

    @Test
    public void emitirEventoRetiroSolicitadoInicioSinFeRetiroNomina() {
        Date fechaConsulta = new Date();

        SolicitudRetiro sr = solicitudRetiro.toBuilder().feRetiroNomina(null).feCreacion(fechaConsulta).build();

        Mockito.when(estadoCuentaGateway.consultarEstadoCuentaSocio(anyString(), anyString(), any()))
                .thenReturn(Mono.just(socio));

        Mockito.when(solicitudRetiroRepository.actualizarSolicitudRetiro(any()))
                .thenReturn(Mono.just(solicitudRetiro));

        Mockito.when(eventBus.emit(Mockito.any(RetiroSolicitadoInicio.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.emitirEventoRetiroSolicitadoInicio(sr))
                .verifyComplete();

        Mockito.verify(eventBus).emit(Mockito.any(RetiroSolicitadoInicio.class));

        Mockito.verify(estadoCuentaGateway).consultarEstadoCuentaSocio(solicitudRetiro.getTipoIdentificacion(), solicitudRetiro.getNmIdentificacion(), fechaConsulta);
    }

    @Test
    public void emitirEventoRetiroSolicitadoInicioSinFeRetiroNominaSinFeCreacion() {
        SolicitudRetiro sr = solicitudRetiro.toBuilder().feRetiroNomina(null).feCreacion(null).build();

        Mockito.when(estadoCuentaGateway.consultarEstadoCuentaSocio(anyString(), anyString(), any()))
                .thenReturn(Mono.just(socio));

        Mockito.when(solicitudRetiroRepository.actualizarSolicitudRetiro(any()))
                .thenReturn(Mono.just(solicitudRetiro));

        Mockito.when(eventBus.emit(Mockito.any(RetiroSolicitadoInicio.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.emitirEventoRetiroSolicitadoInicio(sr))
                .verifyComplete();

        Mockito.verify(eventBus).emit(Mockito.any(RetiroSolicitadoInicio.class));

    }

}