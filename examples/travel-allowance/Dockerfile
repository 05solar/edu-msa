FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY travel-allowance.csproj ./
RUN dotnet restore
COPY Program.cs ./
RUN dotnet publish -c Release -o /app/publish /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:8.0
WORKDIR /app
COPY --from=build /app/publish ./
COPY index.html ./index.html
COPY og.png ./og.png
ENV PORT=8080
# .NET 8 aspnet 이미지 내장 비루트 사용자(app, uid 1654)
USER app
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "ASPNETCORE_URLS=http://+:${PORT:-8080} dotnet travel-allowance.dll"]
