package sura.gfsretiros.usecase.socio;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import sura.gfsretiros.domain.common.DominioOperations;
import sura.gfsretiros.domain.common.EventsGateway;
import sura.gfsretiros.domain.common.ex.BusinessException;
import sura.gfsretiros.domain.common.gateway.MessageLogGateway;
import sura.gfsretiros.domain.retiro.SolicitudRetiro;
import sura.gfsretiros.domain.retiro.estado.Estado;
import sura.gfsretiros.domain.retiro.estado.EstadoRetiro;
import sura.gfsretiros.domain.retiro.estado.Etapa;
import sura.gfsretiros.domain.retiro.estadocuenta.gateway.EstadoCuentaGateway;
import sura.gfsretiros.domain.retiro.events.ErrorActualizar;
import sura.gfsretiros.domain.retiro.events.RetiroSolicitadoInicio;
import sura.gfsretiros.domain.retiro.gateway.SolicitudRetiroRepository;
import sura.gfsretiros.domain.retiro.socio.Socio;
import sura.gfsretiros.usecase.error.ErrorRetiroUseCase;

import java.util.Date;
import java.util.Objects;

@RequiredArgsConstructor
public class ConsultarEstadoCuentaSocioUseCase {

    private final MessageLogGateway messageLogGateway;
    private final EventsGateway eventsGateway;
    private final EstadoCuentaGateway estadoCuentaGateway;
    private final SolicitudRetiroRepository solicitudRetiroRepository;
    private final ErrorRetiroUseCase errorRetiroUseCase;

    private static final String ERROR_EMITIENDO_EVENTO_RETIRO_SOLICITADO_INICIO = "Error al emitir evento Retiro.solicitado.inicio";
    private static final String NOMBRE_EVENTO = "RETIRO.SOLICITADO.INICIO OUT: " + RetiroSolicitadoInicio.EVENT_NAME;
    private static final String NOMBRE_METODO = "emitirEventoRetiroSolicitadoInicio";

    public Mono<Void> emitirEventoRetiroSolicitadoInicio(SolicitudRetiro solicitudRetiro) {
        return estadoCuentaGateway.consultarEstadoCuentaSocio(
                        solicitudRetiro.getTipoIdentificacion(),
                        solicitudRetiro.getNmIdentificacion(),
                        Objects.nonNull(solicitudRetiro.getFeRetiroNomina()) ? solicitudRetiro.getFeRetiroNomina() : (Objects.nonNull(solicitudRetiro.getFeCreacion()) ? solicitudRetiro.getFeCreacion() : new Date())
                )
                .map(socio -> construirSolicitudRetiroActualizada(solicitudRetiro, socio))
                .flatMap(this::realizarValidaciones)
                .flatMap(solicitudRetiroRepository::actualizarSolicitudRetiro)
                .filter(solicitud -> !Estado.RECHAZADO.equals(solicitud.getEstado().getEstado()))
                .flatMap(solicitud -> eventsGateway.emit(new RetiroSolicitadoInicio(solicitud)))
                .onErrorResume(excepcion -> {
                    messageLogGateway.logError(
                            ERROR_EMITIENDO_EVENTO_RETIRO_SOLICITADO_INICIO,
                            NOMBRE_METODO,
                            DominioOperations.construirParametros(solicitudRetiro), excepcion);

                    return eventsGateway.emit(new ErrorActualizar(
                                    DominioOperations.actualizarError(
                                            solicitudRetiro,
                                            ERROR_EMITIENDO_EVENTO_RETIRO_SOLICITADO_INICIO + ":" + excepcion.getMessage()
                                    )
                            )
                    );
                }).doFinally(evento -> messageLogGateway.logMessage(
                                solicitudRetiro.toString(),
                                NOMBRE_EVENTO,
                                "" + solicitudRetiro.getNmSolicitud()
                        )
                );
    }

    private Mono<SolicitudRetiro> realizarValidaciones(SolicitudRetiro solicitudRetiro) {
        String msgError;

        Socio socio = solicitudRetiro.getSocio();

        boolean existeSolicitudEnProceso = Estado.RECHAZADO.equals(solicitudRetiro.getEstado().getEstado());

        boolean existeSocio = Objects.nonNull(socio.getDniSocio()) && !socio.getDniSocio().trim().isEmpty();

        boolean existeNominaSocio = Objects.nonNull(socio.getNomina()) && Objects.nonNull(socio.getNomina().getIdNomina()) && !socio.getNomina().getIdNomina().trim().isEmpty();

        boolean existeClaseVinculacionNominaSocio = Objects.nonNull(socio.getNomina()) && Objects.nonNull(socio.getNomina().getClaseVinculacion());

        if (!existeSolicitudEnProceso && existeSocio && existeNominaSocio && existeClaseVinculacionNominaSocio) {
            return Mono.just(solicitudRetiro);
        } else if (existeSolicitudEnProceso) {
            msgError = BusinessException.Type.SOLICITUDES_RETIRO_ACTIVAS_DUPLICADAS.getMessage();
        } else if (!existeSocio) {
            msgError = BusinessException.Type.ERROR_SOCIO_NO_EXISTE_EN_FONDO.getMessage();
            solicitudRetiro.toBuilder().estado(EstadoRetiro.builder().estado(Estado.RECHAZADO).etapa(Etapa.INGRESO).build()).build();
        } else if (!existeNominaSocio) {
            msgError = BusinessException.Type.ERROR_NOMINA_SOCIO_NO_EXISTE.getMessage();
            solicitudRetiro.toBuilder().estado(EstadoRetiro.builder().estado(Estado.RECHAZADO).etapa(Etapa.INGRESO).build()).build();
        } else {
            msgError = BusinessException.Type.ERROR_NOMINA_SOCIO_SIN_CLASE_VINCULACION.getMessage();
            solicitudRetiro.toBuilder().estado(EstadoRetiro.builder().estado(Estado.RECHAZADO).etapa(Etapa.INGRESO).build()).build();
        }

        return errorRetiroUseCase.guardarError(
                msgError,
                solicitudRetiro.getEstado().getEstado().name(),
                solicitudRetiro.getEstado().getEtapa().name(),
                solicitudRetiro.getNmSolicitud()
        ).flatMap(error -> Mono.just(solicitudRetiro));

    }

    private SolicitudRetiro construirSolicitudRetiroActualizada(SolicitudRetiro solicitudRetiro, Socio socio) {
        return solicitudRetiro.toBuilder()
                .feAfiliacion(socio.getFeIngresoFondo())
                .primerNombre(socio.getNombre1())
                .segundoNombre(socio.getNombre2())
                .primerApellido(socio.getApellido1())
                .segundoApellido(socio.getApellido2())
                .codigoSocio(socio.getCodigoSocio())
                .codigoCia(Objects.nonNull(socio.getNomina()) ? socio.getNomina().getCodigoCia() : null)
                .socio(socio)
                .build();
    }

}
