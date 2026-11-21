package com.makerpixel.contabil.notasync;

public class NotasyncTradutor {

    public static String traduzir(int cstat) {
        return switch (cstat) {
            case 8002 -> "O contador não está vinculado a este CNPJ no SAT da SEF/SC";
            case 118 -> "Lote de documentos distribuído com sucesso";
            case 117 -> "Nenhum documento novo localizado para os parâmetros informados";
            case 110 -> "Uso indevido temporário pelo cliente (bloqueio preventivo da SEF/SC)";
            case 657 -> "Consumo indevido do serviço de distribuição da SEF/SC";
            case 137 -> "Nenhum documento localizado";
            case 138 -> "Documento localizado com sucesso";
            case 215 -> "Falha no schema XML do pedido de distribuição";
            case 280 -> "Certificado de transmissor revogado ou inválido na SEF/SC";
            case 281 -> "Certificado do transmissor com data de validade expirada";
            case 282 -> "Falha de autenticação do certificado transmissor ICP-Brasil";
            case 283 -> "Certificado não autorizado para emitir pedidos neste serviço";
            case 999 -> "Erro não catalogado nos servidores da SEF/SC";
            default -> "Código de retorno SEF/SC " + cstat;
        };
    }

    public static boolean isErroVinculoContador(int cstat) {
        return cstat == 8002;
    }
}
